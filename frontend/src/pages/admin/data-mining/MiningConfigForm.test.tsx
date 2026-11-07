import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { useSharedAuth } from 'auth/session';
import { miningApi, miningRunApi, MiningPreview, MiningValidation, WdmApiError } from 'api/admin/data-mining';
import { acceptedRun } from 'api/admin/data-mining/mining-run-test-data';
import MiningConfigForm from './MiningConfigForm';
jest.mock('auth/session', () => ({ useSharedAuth: jest.fn() }));
const identity = useSharedAuth as unknown as jest.Mock;
const id = '11111111-2222-4333-8444-555555555555';
function preview(): MiningPreview { return { snapshotId: id, algorithm: 'WSPM_PRETREE', artifactVersion: 'CONVERSION_ARTIFACT_V1', sequenceSha256: 'a'.repeat(64), mappingSha256: 'b'.repeat(64),
  minimumSupport: { mode: 'PERCENT', configuredValue: 14, relativeFraction: 0.14, effectiveThreshold: 0.42000000000000004 }, executionEligibility: 'NOT_EVALUATED',
  statistics: { sequenceCount: '3', singleItemsetSequenceCount: '1', multiItemsetSequenceCount: '2', itemsetCount: '6', distinctItemCount: '3', totalItemOccurrences: '9007199254740993', minSequenceLength: '2', maxSequenceLength: '3', averageSequenceLength: 2.6666666666666665 } }; }
function checked(): MiningValidation { return { ...preview(), sequenceCount: '3', configurationStatus: 'SUPPORTED' }; }
let read: jest.SpyInstance, validate: jest.SpyInstance;
function page() { return <MantineProvider><MiningConfigForm initialSnapshotId={id}/></MantineProvider>; }
function value(text = '14') { fireEvent.change(screen.getByLabelText(/Giá trị minsup/), { target: { value: text } }); }
const viewButton = () => screen.getByRole('button', { name: 'Xem trước dữ liệu' });
const checkButton = () => screen.getByRole('button', { name: 'Kiểm tra cấu hình' });
const createButton = () => screen.getByRole('button', { name: 'Tạo lần chạy' });
beforeEach(() => {
  Object.defineProperty(globalThis, 'ResizeObserver', { configurable: true, value: jest.fn(() => ({ observe: jest.fn(), unobserve: jest.fn(), disconnect: jest.fn() })) });
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn(), addEventListener: jest.fn(), removeEventListener: jest.fn() })) });
  identity.mockReturnValue({ user: { userId: 'admin', roles: ['ADMIN'] }, status: 'authenticated', forbidden: false });
  read = jest.spyOn(miningApi, 'preview').mockResolvedValue(preview()); validate = jest.spyOn(miningApi, 'validate').mockResolvedValue(checked());
});
afterEach(() => jest.restoreAllMocks());
it('requires current supported config, creates once, locks input and retains unknown result until explicit reset', async () => {
  let reject!: (error: unknown) => void;
  const post = jest.spyOn(miningRunApi, 'create').mockReturnValue(new Promise((resolve, r) => { reject = r; }));
  render(page()); value(); expect(createButton()).toBeDisabled(); fireEvent.click(checkButton());
  await waitFor(() => expect(createButton()).toBeEnabled()); fireEvent.click(createButton()); fireEvent.click(createButton());
  await waitFor(() => expect(post).toHaveBeenCalledTimes(1)); expect(screen.getByLabelText(/Snapshot ID/)).toBeDisabled();
  await act(async () => { reject(new WdmApiError('http', 503, 'SERVICE_UNAVAILABLE', 'private')); });
  expect(await screen.findByText('Chưa xác định kết quả tạo run')).toBeVisible(); expect(createButton()).toBeDisabled();
  value('15'); expect(screen.getByText('Chưa xác định kết quả tạo run')).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: /Tạo yêu cầu mới/ })); expect(createButton()).toBeDisabled();
});
it('invalidates create eligibility on config edit and passes confirmed acceptance to parent', async () => {
  const onAccepted = jest.fn(); jest.spyOn(miningRunApi, 'create').mockResolvedValue(acceptedRun());
  render(<MantineProvider><MiningConfigForm initialSnapshotId={id} onAccepted={onAccepted}/></MantineProvider>); value();
  fireEvent.click(checkButton()); await waitFor(() => expect(createButton()).toBeEnabled());
  value('15'); expect(createButton()).toBeDisabled(); value('14'); fireEvent.click(checkButton());
  await waitFor(() => expect(createButton()).toBeEnabled()); fireEvent.click(createButton()); await waitFor(() => expect(onAccepted).toHaveBeenCalledWith(acceptedRun()));
});
it('selects both algorithm and absolute mode and sends a fractional configured value', async () => {
  validate.mockResolvedValue({ ...checked(), algorithm: 'WSPM_BITPRUNE', minimumSupport: { mode: 'ABSOLUTE', configuredValue: 0.25, relativeFraction: null, effectiveThreshold: 0.25 } });
  render(page());
  fireEvent.click(screen.getByLabelText('Thuật toán')); fireEvent.mouseDown(screen.getByRole('option', { name: 'WSPM_BitPrune' }));
  fireEvent.click(screen.getByLabelText('Mode minsup')); fireEvent.mouseDown(screen.getByRole('option', { name: 'Tuyệt đối (ABSOLUTE)' }));
  value('0.25'); fireEvent.click(checkButton()); await screen.findByText('Cấu hình thuộc phạm vi hỗ trợ');
  expect(validate).toHaveBeenCalledWith({ snapshotId: id, algorithm: 'WSPM_BITPRUNE', minsupMode: 'ABSOLUTE', minsupValue: 0.25 }, expect.any(AbortSignal));
  expect(screen.getByText('Không áp dụng')).toBeInTheDocument();
});
it.each(['0', '-0.0', '1e-6', '100', '1e-999'])('accepts representable configured input %s and leaves threshold resolution to server', async text => {
  render(page()); value(text); fireEvent.click(viewButton()); await screen.findByText('Thống kê dataset');
  expect(read.mock.calls[0][0].minsupValue).toBe(Number(text));
});
it('keyboard form submission triggers only preview', async () => {
  render(page()); value(); fireEvent.submit(screen.getByRole('form', { name: 'Cấu hình khai phá' }));
  await screen.findByText('Thống kê dataset'); expect(read).toHaveBeenCalledTimes(1); expect(validate).not.toHaveBeenCalled();
});
it('invalid snapshot does not send and remains associated with its field error', () => {
  render(page()); value(); fireEvent.change(screen.getByLabelText(/Snapshot ID/), { target: { value: 'invalid' } });
  fireEvent.click(checkButton()); expect(validate).not.toHaveBeenCalled(); expect(screen.getByText('Nhập UUID snapshot chuẩn, chữ thường.')).toBeInTheDocument();
});
it('network and unknown 422 failures do not imply supported config or expose arbitrary text', async () => {
  validate.mockRejectedValue(new WdmApiError('network', null, 'NETWORK_ERROR', 'private'));
  render(page()); value(); fireEvent.click(checkButton()); await screen.findByRole('alert'); expect(validate).toHaveBeenCalledTimes(1);
  validate.mockRejectedValue(new WdmApiError('http', 422, 'UNKNOWN_CODE', 'private')); fireEvent.click(checkButton());
  await waitFor(() => expect(validate).toHaveBeenCalledTimes(2));
  expect(screen.queryByText('Cấu hình thuộc phạm vi hỗ trợ')).not.toBeInTheDocument(); expect(screen.queryByText(/Ngưỡng hiệu dụng bằng zero/)).not.toBeInTheDocument();
});
it('sends nothing on mount; displays server statistics and distinguishes preview from supported config', async () => {
  render(page()); expect(read).not.toHaveBeenCalled(); expect(validate).not.toHaveBeenCalled(); value(); fireEvent.click(viewButton());
  await screen.findByText('Thống kê dataset'); expect(screen.getByText('9007199254740993')).toBeInTheDocument();
  expect(screen.getByText(/chưa xác nhận cấu hình thuộc phạm vi/)).toBeInTheDocument();
  fireEvent.click(checkButton()); await screen.findByText('Cấu hình thuộc phạm vi hỗ trợ');
  expect(validate).toHaveBeenCalledWith({ snapshotId: id, algorithm: 'WSPM_PRETREE', minsupMode: 'PERCENT', minsupValue: 14 }, expect.any(AbortSignal));
  expect(screen.queryByRole('button', { name: /Chạy khai phá/ })).not.toBeInTheDocument();
});
it.each(['', ' ', 'NaN', 'Infinity', '0x10', '-1', '101', '1e999'])('rejects invalid/empty percent %s without sending', text => {
  render(page()); value(text); fireEvent.click(checkButton()); expect(validate).not.toHaveBeenCalled(); expect(screen.getByText('Nhập số hữu hạn từ 0 đến 100.')).toBeInTheDocument();
});
it('invalidates supported result immediately when any value changes', async () => {
  render(page()); value(); fireEvent.click(checkButton()); await screen.findByText('Cấu hình thuộc phạm vi hỗ trợ');
  value('15'); expect(screen.queryByText('Cấu hình thuộc phạm vi hỗ trợ')).not.toBeInTheDocument(); expect(validate).toHaveBeenCalledTimes(1);
});
it('locks double clicks and ignores late responses after edit even if transport ignores abort', async () => {
  let finish!: (v: MiningValidation) => void; validate.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  render(page()); value(); fireEvent.click(checkButton()); fireEvent.click(checkButton()); expect(validate).toHaveBeenCalledTimes(1);
  const signal = validate.mock.calls[0][1] as AbortSignal; value('15'); expect(signal.aborted).toBe(true);
  await act(async () => finish(checked())); expect(screen.queryByText('Cấu hình thuộc phạm vi hỗ trợ')).not.toBeInTheDocument();
});
it.each(['logout', 'different-user', 'forbidden', 'role-loss', 'token-change'])('clears and suppresses delayed results on %s', async reason => {
  let finish!: (v: MiningValidation) => void; validate.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  const view = render(page()); value(); fireEvent.click(checkButton()); const signal = validate.mock.calls[0][1] as AbortSignal;
  const auth = { user: { userId: reason === 'different-user' ? 'other' : 'admin', roles: reason === 'role-loss' ? ['CUSTOMER'] : ['ADMIN'] },
    status: reason === 'logout' ? 'anonymous' : 'authenticated', forbidden: reason === 'forbidden', accessToken: reason === 'token-change' ? 'new' : undefined };
  identity.mockReturnValue(auth); view.rerender(page()); expect(signal.aborted).toBe(true);
  await act(async () => finish(checked())); expect(screen.queryByText('Cấu hình thuộc phạm vi hỗ trợ')).not.toBeInTheDocument();
});
it('aborts on unmount with no POST replay', async () => {
  validate.mockImplementation(() => new Promise(() => undefined)); const view = render(page()); value(); fireEvent.click(checkButton());
  const signal = validate.mock.calls[0][1] as AbortSignal; view.unmount(); expect(signal.aborted).toBe(true); expect(validate).toHaveBeenCalledTimes(1);
});
it('zero is sent and explains only the whitelisted 422 reason, without exposing server text', async () => {
  validate.mockRejectedValue(new WdmApiError('http', 422, 'UNSUPPORTED_MINING_CONFIGURATION', 'private-text', 'req-zero', [{ field: 'minsupValue', code: 'ZERO_EFFECTIVE_THRESHOLD', message: 'private-text' }]));
  render(page()); value('0'); fireEvent.click(checkButton()); await screen.findByText('Cấu hình ngoài phạm vi hỗ trợ');
  expect(screen.getByText(/Ngưỡng hiệu dụng bằng zero/)).toBeInTheDocument(); expect(screen.getByText('Mã yêu cầu: req-zero')).toBeInTheDocument();
  expect(screen.queryByText(/private-text/)).not.toBeInTheDocument(); expect(validate).toHaveBeenCalledTimes(1);
});
it('clears both results on a changed artifact binding', async () => {
  validate.mockResolvedValue({ ...checked(), mappingSha256: 'c'.repeat(64) }); render(page()); value(); fireEvent.click(viewButton());
  await screen.findByText('Thống kê dataset'); fireEvent.click(checkButton()); await screen.findByText(/Thông tin snapshot đã thay đổi/);
  expect(screen.queryByText('Thống kê dataset')).not.toBeInTheDocument(); expect(screen.queryByText('Cấu hình thuộc phạm vi hỗ trợ')).not.toBeInTheDocument();
});
it.each([400, 404, 409, 503])('shows a safe error for %s and permits only explicit retries', async status => {
  validate.mockRejectedValue(new WdmApiError('http', status, 'TEST_ERROR', 'private', 'req-failure'));
  render(page()); value(); fireEvent.click(checkButton()); await screen.findByRole('alert'); expect(validate).toHaveBeenCalledTimes(1);
  fireEvent.click(checkButton()); await waitFor(() => expect(validate).toHaveBeenCalledTimes(2));
});
