import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { useSharedAuth } from 'auth/session';
import { miningApi, miningRunApi, MiningPreview, MiningRunAccepted, MiningValidation, WdmApiError } from 'api/admin/data-mining';
import MiningConfigForm from './MiningConfigForm';

jest.mock('auth/session', () => ({ useSharedAuth: jest.fn() }));
jest.mock('./SelectedDatasetPanel', () => () => <div/>);
const onAccepted = jest.fn();
const identity = useSharedAuth as unknown as jest.Mock;
const snapshotId = '11111111-2222-4333-8444-555555555555';
const runId = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee';
const input = { snapshotId, algorithm: 'WSPM_PRETREE' as const, minsupMode: 'PERCENT' as const, minsupValue: 14 };
const accepted: MiningRunAccepted = { runId, snapshotId, algorithm: 'WSPM_PRETREE', status: 'QUEUED', createdAt: '2026-11-07T05:00:00.123456Z' };

// Protocol fixtures only: these are not mining output or comparison evidence.
function preview(): MiningPreview {
  return { snapshotId, algorithm: 'WSPM_PRETREE', artifactVersion: 'CONVERSION_ARTIFACT_V1',
    sequenceSha256: 'a'.repeat(64), mappingSha256: 'b'.repeat(64),
    minimumSupport: { mode: 'PERCENT', configuredValue: 14, relativeFraction: .14, effectiveThreshold: .42 },
    executionEligibility: 'NOT_EVALUATED',
    statistics: { sequenceCount: '3', singleItemsetSequenceCount: '1', multiItemsetSequenceCount: '2', itemsetCount: '6',
      distinctItemCount: '3', totalItemOccurrences: '8', minSequenceLength: '2', maxSequenceLength: '3', averageSequenceLength: 8 / 3 } };
}
function validated(): MiningValidation {
  return { snapshotId, algorithm: 'WSPM_PRETREE', artifactVersion: 'CONVERSION_ARTIFACT_V1',
    sequenceSha256: 'a'.repeat(64), mappingSha256: 'b'.repeat(64), minimumSupport: preview().minimumSupport,
    sequenceCount: '3', configurationStatus: 'SUPPORTED' };
}
function deferred() {
  let resolve!: (value: MiningRunAccepted) => void;
  const promise = new Promise<MiningRunAccepted>(done => { resolve = done; });
  return { promise, resolve };
}
const page = () => <MantineProvider><MiningConfigForm initialSnapshotId={snapshotId} onAccepted={onAccepted}/></MantineProvider>;
const createButton = () => screen.getByRole('button', { name: 'Tạo lần chạy' });
const checkButton = () => screen.getByRole('button', { name: 'Kiểm tra cấu hình' });
let post: jest.SpyInstance;

beforeEach(() => {
  onAccepted.mockClear();
  Object.defineProperty(globalThis, 'ResizeObserver', { configurable: true, value: jest.fn(() => ({ observe: jest.fn(), unobserve: jest.fn(), disconnect: jest.fn() })) });
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn(), addEventListener: jest.fn(), removeEventListener: jest.fn() })) });
  identity.mockReturnValue({ user: { userId: 'admin', roles: ['ADMIN'] }, status: 'authenticated', forbidden: false, accessToken: 'test-token' });
  jest.spyOn(miningApi, 'preview').mockResolvedValue(preview());
  jest.spyOn(miningApi, 'validate').mockResolvedValue(validated());
  post = jest.spyOn(miningRunApi, 'create').mockResolvedValue(accepted);
});
afterEach(() => jest.restoreAllMocks());

async function validateInput() {
  fireEvent.change(screen.getByLabelText(/Giá trị minsup/), { target: { value: '14' } });
  fireEvent.click(checkButton());
  await screen.findByText('Cấu hình thuộc phạm vi hỗ trợ');
  await waitFor(() => expect(createButton()).toBeEnabled());
}

it('preserves preview submission and requires current validated input before explicit creation', async () => {
  render(page());
  expect(createButton()).toBeDisabled();
  expect(post).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText(/Giá trị minsup/), { target: { value: '14' } });
  fireEvent.submit(screen.getByRole('form', { name: 'Cấu hình khai phá' }));
  await screen.findByText('Thống kê dataset');
  expect(miningApi.preview).toHaveBeenCalledWith(input, expect.any(AbortSignal));
  expect(miningApi.validate).not.toHaveBeenCalled();
  expect(createButton()).toBeDisabled();
  expect(post).not.toHaveBeenCalled();
  fireEvent.click(checkButton());
  await waitFor(() => expect(createButton()).toBeEnabled());
  fireEvent.change(screen.getByLabelText(/Giá trị minsup/), { target: { value: '15' } });
  expect(createButton()).toBeDisabled();
  expect(post).not.toHaveBeenCalled();
});

it('sends one explicit POST on double click, freezes inputs and hands the accepted RUN to the workspace', async () => {
  const request = deferred(); post.mockReturnValue(request.promise);
  render(page()); await validateInput();
  fireEvent.click(createButton()); fireEvent.click(createButton());
  await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
  expect(post).toHaveBeenCalledWith(input, expect.any(AbortSignal));
  expect(screen.getByLabelText(/Snapshot ID/)).toBeDisabled();
  expect(screen.getByLabelText(/Giá trị minsup/)).toBeDisabled();
  expect(checkButton()).toBeDisabled();
  await act(async () => { request.resolve(accepted); });
  expect(onAccepted).toHaveBeenCalledWith(accepted);
  expect(createButton()).toBeDisabled();
  expect(screen.getByLabelText(/Giá trị minsup/)).toBeEnabled();
  expect(post).toHaveBeenCalledTimes(1);
});

it('keeps an uncertain submission explicit and never automatically replays the POST', async () => {
  post.mockRejectedValue(new WdmApiError('http', 503, 'SERVICE_UNAVAILABLE', 'private', 'request-uncertain'));
  render(page()); await validateInput(); fireEvent.click(createButton());
  await screen.findByText('Chưa xác định kết quả tạo run');
  expect(screen.getByText(/request-uncertain/)).toBeInTheDocument();
  expect(screen.queryByText('RUN ID: ' + runId)).not.toBeInTheDocument();
  expect(createButton()).toBeDisabled();
  expect(post).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole('button', { name: 'Tạo yêu cầu mới và kiểm tra lại' }));
  expect(createButton()).toBeDisabled();
  fireEvent.click(checkButton());
  await waitFor(() => expect(createButton()).toBeEnabled());
  expect(post).toHaveBeenCalledTimes(1);
});

it('aborts on session loss and ignores a late accepted response', async () => {
  const request = deferred(); post.mockReturnValue(request.promise);
  const view = render(page()); await validateInput(); fireEvent.click(createButton());
  await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
  const signal = post.mock.calls[0][1] as AbortSignal;
  identity.mockReturnValue({ user: null, status: 'anonymous', forbidden: false, accessToken: null });
  view.rerender(page());
  await waitFor(() => expect(signal.aborted).toBe(true));
  await act(async () => { request.resolve(accepted); });
  expect(onAccepted).not.toHaveBeenCalled();
  expect(screen.queryByText('RUN ID: ' + runId)).not.toBeInTheDocument();
  expect(createButton()).toBeDisabled();
  expect(post).toHaveBeenCalledTimes(1);
});

it('requires validation again when identity changes after acceptance', async () => {
  const view = render(page()); await validateInput(); fireEvent.click(createButton());
  await waitFor(() => expect(onAccepted).toHaveBeenCalledWith(accepted));
  identity.mockReturnValue({ user: null, status: 'anonymous', forbidden: false, accessToken: null });
  view.rerender(page());
  expect(screen.queryByText('RUN ID: ' + runId)).not.toBeInTheDocument();
  identity.mockReturnValue({ user: { userId: 'other-admin', roles: ['ADMIN'] }, status: 'authenticated', forbidden: false, accessToken: 'other-token' });
  view.rerender(page());
  expect(screen.queryByText('RUN ID: ' + runId)).not.toBeInTheDocument();
  expect(createButton()).toBeDisabled();
  expect(post).toHaveBeenCalledTimes(1);
});
