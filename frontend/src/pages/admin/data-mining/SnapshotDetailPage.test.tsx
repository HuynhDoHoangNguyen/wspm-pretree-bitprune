import React from 'react';
import { runInThisContext } from 'vm';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { MemoryRouter, Outlet, Route, Routes, useNavigate } from 'react-router-dom';
import { useSharedAuth } from 'auth/session';
import { setWdmAuthAdapter } from 'api/admin/data-mining';
import { createDataMiningRoutes } from './routes';
import DataMiningNavigation from './DataMiningNavigation';
import { reportData } from 'api/admin/data-mining/validation-test-data';

jest.mock('auth/session', () => ({ useSharedAuth: jest.fn() }));
Object.assign(globalThis, runInThisContext('({ Headers, Response })'));
const identity = useSharedAuth as unknown as jest.Mock;
const id = '11111111-2222-4333-8444-555555555555';
const otherId = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee';
const detail = { snapshotId: id, sourceType: 'CSV', sourceName: 'original.csv', validationStatus: 'PENDING',
  conversionStatus: 'NOT_STARTED', statisticsStatus: 'NOT_COMPUTED', rawSha256: 'a'.repeat(64), rawSizeBytes: '9007199254740993',
  sequenceCount: null, fromTime: null, toTime: null, createdAt: '2026-11-05T01:02:03.123456Z', rawAvailability: 'AVAILABLE',
  provenance: { contractVersion: 'wdm-order-csv-v1', acquisition: 'CSV_UPLOAD', sourceTimeZone: null,
    timeField: 'ordered_at', statusPolicy: 'COMPLETED_CURRENT_AT_ACQUISITION' } };
let fetchMock: jest.Mock;
let session: AbortController;
function json(data: unknown = detail) {
  return new Response(JSON.stringify({ code: 'SUCCESS', message: 'Request completed successfully.', data, requestId: 'request-1' }),
    { headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'request-1' } });
}
function denied(status: number) {
  return new Response(JSON.stringify({ status, code: 'TEST_DENIAL', message: 'safe error', path: '/api/v1/admin/dataset-snapshots/' + id,
    requestId: 'request-1', details: [] }), { status, headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'request-1' } });
}
function Jump() {
  const navigate = useNavigate();
  return <button onClick={() => navigate('/admin/datasets/' + otherId)}>Mở snapshot khác</button>;
}
function page(snapshotId = id) {
  return <MemoryRouter initialEntries={['/admin/datasets/' + snapshotId]}><MantineProvider>
    <DataMiningNavigation/><Jump/><Routes><Route path="/admin" element={<Outlet/>}>{createDataMiningRoutes()}</Route></Routes>
  </MantineProvider></MemoryRouter>;
}
function field(label: string) {
  return within((screen.getByText(label).closest('dt') as HTMLElement).parentElement as HTMLElement);
}

it('refreshes metadata after validation without remounting/replaying the operation', async () => {
  const report = { ...reportData(), rawSizeBytes: detail.rawSizeBytes };
  fetchMock.mockResolvedValueOnce(json()).mockResolvedValueOnce(json(report))
    .mockResolvedValueOnce(json({ ...detail, validationStatus: 'VALID' }));
  render(page()); await screen.findByText('original.csv');
  fireEvent.click(screen.getByRole('button', { name: 'Kiểm tra dữ liệu', exact: true }));
  await screen.findByText('Có dữ liệu sạch sử dụng được');
  await waitFor(() => expect(field('Trạng thái kiểm tra').getByText('VALID — Có dữ liệu sạch sử dụng được')).toBeInTheDocument());
  expect(fetchMock).toHaveBeenCalledTimes(3);
  expect(fetchMock.mock.calls.map(call => call[1].method)).toEqual(['GET', 'POST', 'GET']);
});
beforeEach(() => {
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(query => ({ matches: false, media: query,
    onchange: null, addListener: jest.fn(), removeListener: jest.fn(), addEventListener: jest.fn(), removeEventListener: jest.fn(), dispatchEvent: jest.fn() })) });
  Object.defineProperty(globalThis, 'ResizeObserver', { configurable: true, value: jest.fn(() => ({ observe: jest.fn(), unobserve: jest.fn(), disconnect: jest.fn() })) });
  fetchMock = jest.fn(); session = new AbortController();
  Object.defineProperty(globalThis, 'fetch', { configurable: true, writable: true, value: fetchMock });
  Object.defineProperty(URL, 'createObjectURL', { configurable: true, writable: true, value: jest.fn(() => 'blob:raw') });
  Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, writable: true, value: jest.fn() });
  jest.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
  identity.mockReturnValue({ user: { roles: ['ADMIN'] }, status: 'authenticated', forbidden: false });
  setWdmAuthAdapter({ getAccessToken: () => 'admin-token', getSessionSignal: () => session.signal });
});
afterEach(() => { setWdmAuthAdapter(null); jest.restoreAllMocks(); });

it('opens the real deep route, renders honest metadata and refetches after remount/reload', async () => {
  fetchMock.mockImplementation(() => Promise.resolve(json()));
  const view = render(page());
  expect(screen.getByRole('status')).toHaveTextContent('Đang tải');
  await screen.findByText('original.csv');
  expect(screen.getByRole('link', { name: 'Dữ liệu khai phá' })).toHaveAttribute('aria-current', 'page');
  expect(field('Số sequence').getByText('Chưa xác định')).toBeInTheDocument();
  expect(field('Thống kê').getByText('Chưa tính')).toBeInTheDocument();
  expect(screen.getByText('9007199254740993 byte')).toBeInTheDocument();
  expect(screen.getByText(detail.rawSha256)).toBeInTheDocument();
  expect(screen.getByText(detail.createdAt)).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /Validate|Convert|Run/ })).not.toBeInTheDocument();
  view.unmount(); render(page()); await screen.findByText('original.csv');
  expect(fetchMock).toHaveBeenCalledTimes(2);
});

it.each(['MISSING', 'UNAVAILABLE'])('shows %s raw availability with metadata and disables download', async availability => {
  fetchMock.mockResolvedValue(json({ ...detail, rawAvailability: availability }));
  render(page()); await screen.findByText('original.csv');
  expect(screen.getByRole('alert')).toHaveTextContent('CSV gốc chưa sẵn sàng');
  expect(screen.getByRole('button', { name: 'Tải CSV gốc' })).toBeDisabled();
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

it('displays unknown metadata without inferring valid/computed state', async () => {
  fetchMock.mockResolvedValue(json({ ...detail, sourceName: null, validationStatus: null, conversionStatus: null, statisticsStatus: null,
    rawSizeBytes: null, rawSha256: null, rawAvailability: 'UNAVAILABLE' }));
  render(page()); await screen.findByText('CSV gốc chưa sẵn sàng');
  expect(field('Số sequence').getByText('Chưa xác định')).toBeInTheDocument();
  expect(field('Trạng thái kiểm tra').getByText('Chưa xác định')).toBeInTheDocument();
  expect(field('Thống kê').getByText('Chưa xác định')).toBeInTheDocument();
});

it.each([400, 401, 403, 404, 503])('shows metadata HTTP %s safely and allows explicit retry', async status => {
  fetchMock.mockResolvedValueOnce(denied(status)).mockResolvedValueOnce(json());
  render(page());
  expect(await screen.findByRole('alert')).toHaveTextContent('Mã yêu cầu: request-1');
  expect(screen.queryByText('original.csv')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Tải lại chi tiết' }));
  await screen.findByText('original.csv');
  expect(fetchMock).toHaveBeenCalledTimes(2);
});

it('rejects a malformed URL ID locally and does not fetch', async () => {
  render(page('bad-id')); expect(await screen.findByRole('alert')).toHaveTextContent('Snapshot ID không hợp lệ');
  expect(fetchMock).not.toHaveBeenCalled();
});

it('downloads CSV through the actual client, prevents duplicate clicks and revokes its URL', async () => {
  let finish!: (response: Response) => void;
  fetchMock.mockResolvedValueOnce(json()).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  render(page()); await screen.findByText('original.csv');
  const button = screen.getByRole('button', { name: 'Tải CSV gốc' });
  fireEvent.click(button); fireEvent.click(button);
  expect(button).toBeDisabled(); expect(fetchMock).toHaveBeenCalledTimes(2);
  expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/admin/dataset-snapshots/' + id + '/artifacts/raw');
  await act(async () => { finish(new Response('raw\r\n', { headers: { 'Content-Type': 'text/csv' } })); });
  expect(await screen.findByText('Đã gửi CSV gốc để trình duyệt tải xuống.')).toBeInTheDocument();
  const anchor = (HTMLAnchorElement.prototype.click as jest.Mock).mock.instances[0] as HTMLAnchorElement;
  expect(anchor.download).toBe('snapshot-' + id + '-raw.csv');
  await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:raw'));
});

it.each([404, 503])('retains detail on raw download HTTP %s without creating a file', async status => {
  fetchMock.mockResolvedValueOnce(json()).mockResolvedValueOnce(denied(status));
  render(page()); await screen.findByText('original.csv');
  fireEvent.click(screen.getByRole('button', { name: 'Tải CSV gốc' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Chưa thể tải CSV gốc');
  expect(screen.getByText('original.csv')).toBeInTheDocument();
  expect(URL.createObjectURL).not.toHaveBeenCalled();
});

it('never allows a late response for a different ID to replace the active detail', async () => {
  let finish!: (response: Response) => void;
  fetchMock.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }))
    .mockResolvedValueOnce(json({ ...detail, snapshotId: otherId, sourceName: 'second.csv' }));
  render(page()); const signal = fetchMock.mock.calls[0][1].signal as AbortSignal;
  fireEvent.click(screen.getByRole('button', { name: 'Mở snapshot khác' }));
  await screen.findByText('second.csv'); expect(signal.aborted).toBe(true);
  await act(async () => { finish(json()); });
  expect(screen.queryByText('original.csv')).not.toBeInTheDocument();
  expect(screen.getByText('second.csv')).toBeInTheDocument();
});

it('aborts a pending raw download on unmount and ignores its late bytes', async () => {
  let finish!: (response: Response) => void;
  fetchMock.mockResolvedValueOnce(json()).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  const view = render(page()); await screen.findByText('original.csv');
  fireEvent.click(screen.getByRole('button', { name: 'Tải CSV gốc' }));
  const signal = fetchMock.mock.calls[1][1].signal as AbortSignal;
  view.unmount(); expect(signal.aborted).toBe(true);
  await act(async () => { finish(new Response('raw', { headers: { 'Content-Type': 'text/csv' } })); });
  expect(URL.createObjectURL).not.toHaveBeenCalled();
});

it('ends pending reads when the shared session is cleared', async () => {
  let finish!: (response: Response) => void;
  fetchMock.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  const view = render(page()); const signal = fetchMock.mock.calls[0][1].signal as AbortSignal;
  session.abort(); identity.mockReturnValue({ user: null, status: 'anonymous', forbidden: false });
  view.rerender(page()); expect(signal.aborted).toBe(true);
  await act(async () => { finish(json()); });
  expect(screen.getByRole('alert')).toHaveTextContent('Cần đăng nhập');
  expect(screen.queryByText('original.csv')).not.toBeInTheDocument();
});
