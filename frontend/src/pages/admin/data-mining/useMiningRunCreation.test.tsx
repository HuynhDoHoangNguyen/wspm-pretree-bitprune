import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { useSharedAuth } from 'auth/session';
import { miningRunApi, WdmApiError, MiningRunAccepted } from 'api/admin/data-mining';
import { acceptedRun, runInput, validatedRun } from 'api/admin/data-mining/mining-run-test-data';
import { useMiningRunCreation } from './useMiningRunCreation';
import { RUN_POST_TIMEOUT } from './mining-run-lifecycle';
jest.mock('auth/session', () => ({ useSharedAuth: jest.fn() }));
const accepted = jest.fn();
let post: jest.SpyInstance;
function Harness({ supported = true }: { supported?: boolean }) {
  const state = useMiningRunCreation(supported ? validatedRun() : null, accepted);
  return <><button onClick={() => void state.create(runInput)}>Create</button><button onClick={state.reset}>Reset</button><span data-testid="phase">{state.phase}</span></>;
}
async function flush() { await act(async () => { for (let i = 0; i < 8; i++) await Promise.resolve(); }); }
beforeEach(() => {
  jest.useFakeTimers(); accepted.mockReset();
  (useSharedAuth as unknown as jest.Mock).mockReturnValue({ status: 'authenticated', user: { userId: 'admin', roles: ['ADMIN'] }, forbidden: false });
  post = jest.spyOn(miningRunApi, 'create');
});
afterEach(() => { jest.restoreAllMocks(); jest.useRealTimers(); });
it('requires supported binding and never creates on mount', async () => {
  render(<Harness supported={false}/>); fireEvent.click(screen.getByText('Create')); await flush(); expect(post).not.toHaveBeenCalled();
});
it('creates once for double click and does not replay an accepted request', async () => {
  post.mockResolvedValue(acceptedRun()); render(<Harness/>);
  fireEvent.click(screen.getByText('Create')); fireEvent.click(screen.getByText('Create')); await flush();
  expect(post).toHaveBeenCalledTimes(1); expect(accepted).toHaveBeenCalledWith(acceptedRun());
  fireEvent.click(screen.getByText('Create')); await flush(); expect(post).toHaveBeenCalledTimes(1);
});
it.each([new WdmApiError('http', 503, 'SERVICE_UNAVAILABLE', 'private'), new WdmApiError('network', null, 'NETWORK_ERROR', 'private'),
  new WdmApiError('response', 202, 'INVALID_RUN_RESPONSE', 'private')])('keeps uncertainty and requires explicit new intent', async error => {
  post.mockRejectedValue(error); render(<Harness/>); fireEvent.click(screen.getByText('Create')); await flush();
  expect(screen.getByTestId('phase')).toHaveTextContent('uncertain');
  fireEvent.click(screen.getByText('Create')); await flush(); expect(post).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByText('Reset')); fireEvent.click(screen.getByText('Create')); await flush(); expect(post).toHaveBeenCalledTimes(2);
});
it('bounds an unresponsive POST without server cancellation claims or replay', async () => {
  post.mockImplementation(() => new Promise(() => undefined)); render(<Harness/>); fireEvent.click(screen.getByText('Create')); await flush();
  await act(async () => { jest.advanceTimersByTime(RUN_POST_TIMEOUT); }); await flush();
  expect(screen.getByTestId('phase')).toHaveTextContent('uncertain'); expect(post.mock.calls[0][1].aborted).toBe(true); expect(post).toHaveBeenCalledTimes(1);
});
it('ignores late response after identity change or unmount', async () => {
  let resolve!: (value: MiningRunAccepted) => void; post.mockReturnValue(new Promise(r => { resolve = r; }));
  const view = render(<Harness/>); fireEvent.click(screen.getByText('Create')); await flush();
  (useSharedAuth as unknown as jest.Mock).mockReturnValue({ status: 'anonymous', user: null }); view.rerender(<Harness/>);
  await act(async () => { resolve(acceptedRun()); }); await flush(); expect(accepted).not.toHaveBeenCalled();
  expect(post.mock.calls[0][1].aborted).toBe(true); view.unmount();
});
it('an aborted old request cannot overwrite a newer request after an identity ABA change', async () => {
  let oldResolve!: (value: MiningRunAccepted) => void, nextResolve!: (value: MiningRunAccepted) => void;
  post.mockReturnValueOnce(new Promise(r => { oldResolve = r; })).mockReturnValueOnce(new Promise(r => { nextResolve = r; }));
  const admin = { status: 'authenticated', user: { userId: 'admin', roles: ['ADMIN'] }, forbidden: false };
  const view = render(<Harness/>); fireEvent.click(screen.getByText('Create')); await flush();
  (useSharedAuth as unknown as jest.Mock).mockReturnValue({ status: 'anonymous', user: null }); view.rerender(<Harness/>);
  (useSharedAuth as unknown as jest.Mock).mockReturnValue(admin); view.rerender(<Harness/>);
  fireEvent.click(screen.getByText('Create')); await flush();
  expect(screen.getByTestId('phase')).toHaveTextContent('submitting');
  await act(async () => { oldResolve(acceptedRun()); }); await flush(); expect(accepted).not.toHaveBeenCalled();
  await act(async () => { nextResolve(acceptedRun()); }); await flush(); expect(accepted).toHaveBeenCalledTimes(1);
});
