import React from 'react';
import { runInThisContext } from 'vm';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { useSharedAuth } from 'auth/session';
import { setWdmAuthAdapter } from 'api/admin/data-mining';
import DataMiningGuard from './DataMiningGuard';
import DatasetsPage from './DatasetsPage';

jest.mock('auth/session', () => ({ useSharedAuth: jest.fn() }));
Object.assign(globalThis, runInThisContext('({ Headers, Response })'));
const identity = useSharedAuth as unknown as jest.Mock;
const id = '11111111-2222-4333-8444-555555555555';
const receipt = {
  snapshotId: id, sourceType: 'CSV', sourceName: 'orders.csv', validationStatus: 'PENDING',
  conversionStatus: 'NOT_STARTED', statisticsStatus: 'NOT_COMPUTED', rawSha256: 'a'.repeat(64),
  rawSizeBytes: '875', sequenceCount: null, fromTime: null, toTime: null, createdAt: '2026-11-05T00:00:00Z',
};
let fetchMock: jest.Mock, unauthorized: jest.Mock, forbidden: jest.Mock;
let session: AbortController;

function page() {
  return <MemoryRouter initialEntries={['/admin/datasets?tab=create']}><MantineProvider><DataMiningGuard><DatasetsPage/></DataMiningGuard></MantineProvider></MemoryRouter>;
}
function exportForm() { return screen.getByRole('form', { name: 'Xuất dữ liệu đơn hàng' }); }
function uploadForm() { return screen.getByRole('form', { name: 'Upload CSV' }); }
function period(first = '2026-11-01', last = '2026-11-02') {
  fireEvent.change(screen.getByLabelText(/Từ ngày/), { target: { value: first } });
  fireEvent.change(screen.getByLabelText(/Đến hết ngày/), { target: { value: last } });
}
function choose(file = new File(['original csv'], 'orders.csv', { type: 'text/csv' })) {
  fireEvent.change(screen.getByLabelText('Tệp CSV'), { target: { files: [file] } });
  return file;
}
function success(value: unknown = receipt, status = 201) {
  return new Response(JSON.stringify({ code: 'SUCCESS', message: 'Request completed successfully.', data: value, requestId: 'request-1' }), {
    status, headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'request-1' },
  });
}
function denied(status: number) {
  return new Response(JSON.stringify({ status, code: 'TEST_DENIAL', message: 'safe error',
    path: '/api/v1/admin/dataset-snapshots', requestId: 'request-1', details: [],
  }), { status, headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'request-1' } });
}

beforeEach(() => {
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(query => ({
    matches: false, media: query, onchange: null, addListener: jest.fn(), removeListener: jest.fn(),
    addEventListener: jest.fn(), removeEventListener: jest.fn(), dispatchEvent: jest.fn(),
  })) });
  Object.defineProperty(globalThis, 'ResizeObserver', { configurable: true, value: jest.fn(() => ({
    observe: jest.fn(), unobserve: jest.fn(), disconnect: jest.fn(),
  })) });
  fetchMock = jest.fn(); unauthorized = jest.fn(); forbidden = jest.fn(); session = new AbortController();
  Object.defineProperty(globalThis, 'fetch', { configurable: true, writable: true, value: fetchMock });
  Object.defineProperty(URL, 'createObjectURL', { configurable: true, writable: true, value: jest.fn(() => 'blob:dataset') });
  Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, writable: true, value: jest.fn() });
  jest.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
  identity.mockReturnValue({ user: { roles: ['ADMIN'] }, status: 'authenticated', forbidden: false });
  setWdmAuthAdapter({ getAccessToken: () => 'admin-token', getSessionSignal: () => session.signal,
    onUnauthorized: unauthorized, onForbidden: forbidden });
});
afterEach(() => { setWdmAuthAdapter(null); jest.restoreAllMocks(); });

it('separates list/create tabs, preserves list filters and form input across switching/back', () => {
  function History() {
    const location = useLocation(), navigate = useNavigate();
    return <><output aria-label="Địa chỉ đang mở">{location.search}</output><button onClick={() => navigate(-1)}>Quay lại</button></>;
  }
  render(<MemoryRouter initialEntries={['/admin/datasets?search=orders&page=2']}><MantineProvider><History/><DatasetsPage/></MantineProvider></MemoryRouter>);
  const list = screen.getByRole('tab', { name: 'Danh sách dữ liệu' });
  const create = screen.getByRole('tab', { name: 'Tạo dữ liệu mới · Xuất đơn hàng hoặc upload CSV' });
  expect(list).toHaveAttribute('aria-selected', 'true');
  expect(screen.queryByRole('form', { name: 'Upload CSV' })).not.toBeInTheDocument();
  fireEvent.click(create); expect(create).toHaveAttribute('aria-selected', 'true');
  expect(screen.getByLabelText('Địa chỉ đang mở')).toHaveTextContent('search=orders&page=2&tab=create');
  expect(uploadForm()).toBeVisible();
  fireEvent.change(screen.getByLabelText(/Từ ngày/), { target: { value: '2026-11-01' } });
  fireEvent.click(list); expect(screen.getByLabelText('Tìm tên file hoặc Snapshot ID')).toHaveValue('orders');
  fireEvent.click(screen.getByRole('button', { name: 'Quay lại', exact: true }));
  expect(create).toHaveAttribute('aria-selected', 'true'); expect(screen.getByLabelText(/Từ ngày/)).toHaveValue('2026-11-01');
  expect(fetchMock).not.toHaveBeenCalled();
});
it('supports keyboard tab selection without submitting either form', () => {
  render(page());
  const create = screen.getByRole('tab', { name: 'Tạo dữ liệu mới · Xuất đơn hàng hoặc upload CSV' });
  fireEvent.keyDown(create, { key: 'Home' });
  expect(screen.getByRole('tab', { name: 'Danh sách dữ liệu' })).toHaveFocus();
  fireEvent.keyDown(screen.getByRole('tab', { name: 'Danh sách dữ liệu' }), { key: 'ArrowRight' });
  expect(create).toHaveFocus(); expect(uploadForm()).toBeVisible(); expect(fetchMock).not.toHaveBeenCalled();
});

it('renders real forms without unsolicited API calls or unsupported pipeline actions', () => {
  render(page());
  expect(screen.getByRole('heading', { level: 2, name: 'Dữ liệu khai phá' })).toBeInTheDocument();
  expect(screen.getByText(/giờ Việt Nam/)).toHaveTextContent('UTC+07');
  expect(within(uploadForm()).getByRole('button', { name: /Upload CSV|Đang upload/ })).toBeDisabled();
  expect(screen.queryByText('Chức năng đang được chuẩn bị')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /Validate|Convert|Run/ })).not.toBeInTheDocument();
  expect(fetchMock).not.toHaveBeenCalled();
});

it.each([null, { roles: ['CUSTOMER'] }])('keeps the Dataset route guarded for identity %j', user => {
  identity.mockReturnValue({ user, status: user ? 'authenticated' : 'anonymous', forbidden: false });
  render(page());
  expect(screen.getByRole('alert')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Export CSV' })).not.toBeInTheDocument();
  expect(fetchMock).not.toHaveBeenCalled();
});

it('validates required and reversed dates locally without querying the backend', () => {
  render(page());
  fireEvent.submit(exportForm());
  expect(screen.getByRole('alert')).toHaveTextContent('Chọn đủ ngày');
  period('2026-11-02', '2026-11-01');
  fireEvent.submit(exportForm());
  expect(screen.getByRole('alert')).toHaveTextContent('Ngày bắt đầu phải trước hoặc bằng');
  expect(fetchMock).not.toHaveBeenCalled();
});

it('downloads header-only CSV with correct UTC bounds, releases its URL and never auto-uploads', async () => {
  fetchMock.mockResolvedValue(new Response('order_id,order_item_id\r\n', { headers: {
    'Content-Type': 'text/csv', 'Content-Disposition': 'attachment; filename="order-export-' + id + '.csv"',
  } }));
  render(page()); period(); fireEvent.submit(exportForm());
  await screen.findByText(/Đã gửi tệp CSV/);
  const [path, options] = fetchMock.mock.calls[0];
  const url = new URL(path, 'https://example.test');
  expect(url.searchParams.get('fromTime')).toBe('2026-10-31T17:00:00.000Z');
  expect(url.searchParams.get('toTime')).toBe('2026-11-02T17:00:00.000Z');
  expect(options.headers.get('Authorization')).toBe('Bearer admin-token');
  expect(options.method).toBe('GET');
  expect(HTMLAnchorElement.prototype.click).toHaveBeenCalledTimes(1);
  const anchor = (HTMLAnchorElement.prototype.click as jest.Mock).mock.instances[0] as HTMLAnchorElement;
  expect(anchor.download).toBe('order-export-' + id + '.csv');
  expect(document.contains(anchor)).toBe(false);
  await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:dataset'));
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

it('blocks duplicate export submits and never downloads JSON as CSV', async () => {
  let finish!: (response: Response) => void;
  fetchMock.mockReturnValue(new Promise(resolve => { finish = resolve; }));
  render(page()); period(); fireEvent.submit(exportForm()); fireEvent.submit(exportForm());
  expect(within(exportForm()).getByRole('button')).toBeDisabled();
  expect(fetchMock).toHaveBeenCalledTimes(1);
  await act(async () => { finish(success({}, 200)); });
  expect(await screen.findByRole('alert')).toHaveTextContent('Chưa thể xuất CSV');
  expect(URL.createObjectURL).not.toHaveBeenCalled();
});

it('uploads once, waits for actual completion and shows the real PENDING receipt', async () => {
  let finish!: (response: Response) => void;
  fetchMock.mockReturnValue(new Promise(resolve => { finish = resolve; }));
  render(page()); const file = choose(); fireEvent.submit(uploadForm()); fireEvent.submit(uploadForm());
  expect(within(uploadForm()).getByRole('button', { name: /Upload CSV|Đang upload/ })).toBeDisabled();
  expect(screen.queryByText('Đã tạo snapshot')).not.toBeInTheDocument();
  expect(fetchMock).toHaveBeenCalledTimes(1);
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/dataset-snapshots');
  expect(fetchMock.mock.calls[0][1].body.get('file').name).toBe(file.name);
  expect(fetchMock.mock.calls[0][1].body.get('file').size).toBe(file.size);
  expect(fetchMock.mock.calls[0][1].headers.has('Content-Type')).toBe(false);
  await act(async () => { finish(success()); });
  expect(await screen.findByRole('status')).toHaveTextContent('Đã lưu dữ liệu thành công');
  const receipt = screen.getByRole('region', { name: 'Snapshot đã tạo' });
  expect(receipt).toHaveTextContent(id);
  expect(receipt).toHaveTextContent('Chờ kiểm tra dữ liệu');
  expect(screen.getByRole('link', { name: 'Tiếp tục kiểm tra dữ liệu' })).toHaveAttribute('href', '/admin/datasets/' + id + '?tab=validation');
  expect(screen.getByRole('link', { name: 'Xem chi tiết snapshot' })).toHaveAttribute('href', '/admin/datasets/' + id);
  expect(within(uploadForm()).getByRole('button', { name: /Upload CSV|Đang upload/ })).toBeDisabled();
  expect((screen.getByLabelText('Tệp CSV') as HTMLInputElement).value).toBe('');
  expect(URL.createObjectURL).not.toHaveBeenCalled();
});

it('rejects empty and non-CSV files before POST', () => {
  render(page());
  choose(new File([], 'empty.csv')); fireEvent.submit(uploadForm());
  expect(screen.getByRole('alert')).toHaveTextContent('Tệp đang rỗng');
  choose(new File(['not csv'], 'orders.exe')); fireEvent.submit(uploadForm());
  expect(screen.getByRole('alert')).toHaveTextContent('phần mở rộng .csv');
  expect(fetchMock).not.toHaveBeenCalled();
});

it('allows clearing the selected file with the remove button', () => {
  render(page());
  expect(screen.getByText('Chọn tệp CSV…')).toBeInTheDocument();
  choose(new File(['line1,line2'], 'my-data.csv', { type: 'text/csv' }));
  expect(screen.getByText('my-data.csv')).toBeInTheDocument();
  expect(within(uploadForm()).getByRole('button', { name: /Upload CSV|Đang upload/ })).toBeEnabled();

  fireEvent.click(screen.getByLabelText('Bỏ chọn tệp'));
  expect(screen.queryByText('my-data.csv')).not.toBeInTheDocument();
  expect(screen.getByText('Chọn tệp CSV…')).toBeInTheDocument();
  expect(within(uploadForm()).getByRole('button', { name: /Upload CSV|Đang upload/ })).toBeDisabled();
  expect((screen.getByLabelText('Tệp CSV') as HTMLInputElement).value).toBe('');
});

it.each([400, 401, 403, 413, 415, 503])('shows upload HTTP %s safely with request ID and no replay', async status => {
  fetchMock.mockResolvedValue(denied(status));
  render(page()); choose(); fireEvent.submit(uploadForm());
  const alert = await screen.findByRole('alert');
  expect(alert).toHaveTextContent('Mã yêu cầu: request-1');
  expect(screen.queryByText('Đã tạo snapshot')).not.toBeInTheDocument();
  expect(fetchMock).toHaveBeenCalledTimes(1);
  expect(unauthorized).toHaveBeenCalledTimes(Number(status === 401));
  expect(forbidden).toHaveBeenCalledTimes(Number(status === 403));
  expect(alert.textContent?.includes('có thể đã tạo snapshot')).toBe(status === 503);
});

it('offers a narrower period on export 413 without creating a download', async () => {
  fetchMock.mockResolvedValue(denied(413));
  render(page()); period(); fireEvent.submit(exportForm());
  expect(await screen.findByRole('alert')).toHaveTextContent('thu hẹp khoảng thời gian');
  expect(URL.createObjectURL).not.toHaveBeenCalled();
});

it('shows an uncertain upload outcome on network loss and never auto-retries', async () => {
  fetchMock.mockRejectedValue(new TypeError('private network diagnostics'));
  render(page()); choose(); fireEvent.submit(uploadForm());
  expect(await screen.findByRole('alert')).toHaveTextContent('có thể đã tạo snapshot');
  expect(screen.getByRole('alert')).not.toHaveTextContent('private network diagnostics');
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

it('does not claim success for an invalid 201 receipt or an unexpected 200', async () => {
  fetchMock.mockResolvedValueOnce(success({ ...receipt, sequenceCount: '0' })).mockResolvedValueOnce(success(receipt, 200));
  render(page()); choose(); fireEvent.submit(uploadForm());
  expect(await screen.findByRole('alert')).toHaveTextContent('có thể đã tạo snapshot');
  fireEvent.submit(uploadForm());
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  await screen.findByRole('alert');
  expect(screen.queryByText('Đã tạo snapshot')).not.toBeInTheDocument();
});

it('aborts on navigation/unmount and ignores a late successful export', async () => {
  let finish!: (response: Response) => void;
  fetchMock.mockReturnValue(new Promise(resolve => { finish = resolve; }));
  const view = render(page()); period(); fireEvent.submit(exportForm());
  const signal = fetchMock.mock.calls[0][1].signal as AbortSignal;
  view.unmount(); expect(signal.aborted).toBe(true);
  await act(async () => { finish(new Response('csv', { headers: { 'Content-Type': 'text/csv' } })); });
  expect(URL.createObjectURL).not.toHaveBeenCalled();
});

it('discards pending upload state when the shared session ends', async () => {
  let finish!: (response: Response) => void;
  fetchMock.mockReturnValue(new Promise(resolve => { finish = resolve; }));
  const view = render(page()); choose(); fireEvent.submit(uploadForm());
  const signal = fetchMock.mock.calls[0][1].signal as AbortSignal;
  session.abort(); identity.mockReturnValue({ user: null, status: 'anonymous', forbidden: false });
  view.rerender(page()); expect(signal.aborted).toBe(true);
  await act(async () => { finish(success()); });
  expect(screen.getByRole('alert')).toHaveTextContent('Cần đăng nhập');
  expect(screen.queryByText('Đã tạo snapshot')).not.toBeInTheDocument();
  expect(fetchMock).toHaveBeenCalledTimes(1);
});
