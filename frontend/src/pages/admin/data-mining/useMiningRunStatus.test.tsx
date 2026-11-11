import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { useSharedAuth } from 'auth/session';
import { miningRunApi, MiningRunStatus, WdmApiError } from 'api/admin/data-mining';
import { runId, runStatus } from 'api/admin/data-mining/mining-run-test-data';
import { useMiningRunStatus } from './useMiningRunStatus';
import { RUN_GET_TIMEOUT, RUN_POLL_BUDGET, RUN_POLL_DELAY } from './mining-run-lifecycle';
jest.mock('auth/session', () => ({ useSharedAuth: jest.fn() }));
let get: jest.SpyInstance;
function Harness({ id = runId }: { id?: string }) {
  const state = useMiningRunStatus(id);
  return <><button onClick={() => void state.read()}>Read</button><span data-testid="state">{JSON.stringify({ status: state.report?.status, paused: state.paused, busy: state.busy, error: state.error?.status })}</span></>;
}
async function flush() { await act(async () => { for (let i = 0; i < 8; i++) await Promise.resolve(); }); }
async function tick(ms: number) { await act(async () => { jest.advanceTimersByTime(ms); }); await flush(); }
beforeEach(() => {
  jest.useFakeTimers(); Object.defineProperty(document, 'hidden', { configurable: true, value: false });
  (useSharedAuth as unknown as jest.Mock).mockReturnValue({ status: 'authenticated', user: { userId: 'admin', roles: ['ADMIN'] }, forbidden: false });
  get = jest.spyOn(miningRunApi, 'read').mockResolvedValue(runStatus());
});
afterEach(() => { jest.restoreAllMocks(); jest.useRealTimers(); });
it('polls serially after completion then stops at terminal', async () => {
  let resolve!: (value: MiningRunStatus) => void; get.mockReturnValueOnce(new Promise(r => { resolve = r; })).mockResolvedValue(runStatus('SUCCEEDED'));
  render(<Harness/>); await flush(); await tick(10000); expect(get).toHaveBeenCalledTimes(1);
  await act(async () => { resolve(runStatus()); }); await flush(); await tick(RUN_POLL_DELAY - 1); expect(get).toHaveBeenCalledTimes(1);
  await tick(1); expect(get).toHaveBeenCalledTimes(2); await tick(10000); expect(get).toHaveBeenCalledTimes(2);
});
it('keeps last confirmed snapshot on GET error and resumes by explicit GET only', async () => {
  get.mockResolvedValueOnce(runStatus()).mockRejectedValueOnce(new WdmApiError('http', 503, 'SERVICE_UNAVAILABLE', 'private')).mockResolvedValue(runStatus('FAILED'));
  render(<Harness/>); await flush(); await tick(RUN_POLL_DELAY);
  expect(screen.getByTestId('state')).toHaveTextContent('RUNNING'); expect(screen.getByTestId('state')).toHaveTextContent('"paused":true');
  await tick(10000); expect(get).toHaveBeenCalledTimes(2); fireEvent.click(screen.getByText('Read')); await flush();
  expect(screen.getByTestId('state')).toHaveTextContent('FAILED');
});
it('bounds request duration and rejects a late result from timed out GET', async () => {
  let resolve!: (value: MiningRunStatus) => void; get.mockReturnValueOnce(new Promise(r => { resolve = r; }));
  render(<Harness/>); await flush(); await tick(RUN_GET_TIMEOUT); expect(screen.getByTestId('state')).toHaveTextContent('"paused":true');
  await act(async () => { resolve(runStatus('SUCCEEDED')); }); await flush(); expect(screen.getByTestId('state')).not.toHaveTextContent('SUCCEEDED');
});
it('enforces observation budget and manual resume starts a new GET session', async () => {
  render(<Harness/>); await flush();
  for (let i = 0; i < RUN_POLL_BUDGET / RUN_POLL_DELAY; i++) await tick(RUN_POLL_DELAY);
  expect(screen.getByTestId('state')).toHaveTextContent('"paused":true'); const count = get.mock.calls.length;
  await tick(10000); expect(get).toHaveBeenCalledTimes(count); fireEvent.click(screen.getByText('Read')); await flush(); expect(get).toHaveBeenCalledTimes(count + 1);
});
it('hidden page aborts request; visible page may GET again without stale error', async () => {
  let resolve!: (value: MiningRunStatus) => void; get.mockReturnValueOnce(new Promise(r => { resolve = r; })).mockResolvedValue(runStatus('SUCCEEDED'));
  render(<Harness/>); await flush();
  act(() => { Object.defineProperty(document, 'hidden', { configurable: true, value: true }); document.dispatchEvent(new Event('visibilitychange')); });
  await flush(); expect(get.mock.calls[0][1].aborted).toBe(true); await tick(20000); expect(get).toHaveBeenCalledTimes(1);
  act(() => { Object.defineProperty(document, 'hidden', { configurable: true, value: false }); document.dispatchEvent(new Event('visibilitychange')); }); await flush();
  await act(async () => { resolve(runStatus('RUNNING')); }); await flush(); expect(screen.getByTestId('state')).toHaveTextContent('SUCCEEDED');
});
it('identity changes clear protected snapshots and suppress old responses', async () => {
  const view = render(<Harness/>); await flush(); expect(screen.getByTestId('state')).toHaveTextContent('RUNNING');
  (useSharedAuth as unknown as jest.Mock).mockReturnValue({ status: 'anonymous', user: null });
  view.rerender(<Harness/>); await flush(); expect(get).toHaveBeenCalledTimes(1);
  expect(screen.getByTestId('state')).not.toHaveTextContent('RUNNING'); view.unmount();
});
it('offers paused state when initial read was hidden until its budget expired', async () => {
  get.mockImplementationOnce(() => new Promise(() => undefined)); render(<Harness/>); await flush();
  act(() => { Object.defineProperty(document, 'hidden', { configurable: true, value: true }); document.dispatchEvent(new Event('visibilitychange')); });
  await tick(RUN_POLL_BUDGET);
  act(() => { Object.defineProperty(document, 'hidden', { configurable: true, value: false }); document.dispatchEvent(new Event('visibilitychange')); }); await flush();
  expect(screen.getByTestId('state')).toHaveTextContent('"paused":true'); expect(get).toHaveBeenCalledTimes(1);
});
it('route changes and unmount invalidate outstanding requests', async () => {
  let resolve!: (value: MiningRunStatus) => void; get.mockReturnValueOnce(new Promise(r => { resolve = r; })).mockResolvedValue(runStatus('FAILED'));
  const view = render(<Harness/>); await flush(); view.rerender(<Harness id="aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"/>); await flush();
  await act(async () => { resolve(runStatus()); }); await flush(); expect(screen.getByTestId('state')).toHaveTextContent('FAILED');
  view.unmount(); await tick(10000); expect(get).toHaveBeenCalledTimes(2);
});
it.each([401, 403, 404])('stops automatic reads on HTTP %s', async status => {
  get.mockRejectedValueOnce(new WdmApiError('http', status, 'ERROR', 'private')); render(<Harness/>); await flush(); await tick(10000);
  expect(get).toHaveBeenCalledTimes(1); expect(screen.getByTestId('state')).toHaveTextContent('"paused":true');
});
