import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { useSharedAuth } from 'auth/session';
import { comboInputApi } from 'api/admin/data-mining/combo-input';
import { WdmApiError } from 'api/admin/data-mining/client';
import PublishedComboInputPanel from './PublishedComboInputPanel';
jest.mock('auth/session', () => ({ useSharedAuth: jest.fn() }));
const id = '10000000-0000-4000-8000-000000000001';
beforeEach(() => {
  (useSharedAuth as unknown as jest.Mock).mockReturnValue({ status: 'authenticated', accessToken: 'token', user: { userId: 'admin', roles: ['ADMIN'] }, forbidden: false });
  Object.defineProperty(document, 'hidden', { configurable: true, value: false });
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() })) });
});
afterEach(() => jest.restoreAllMocks());
const open = (path: string) => render(<MemoryRouter initialEntries={[path]}><PublishedComboInputPanel/></MemoryRouter>);
test('does not fetch when listing existing Combos without a mining context', () => {
  const read = jest.spyOn(comboInputApi, 'read'); open('/admin/combos');
  expect(read).not.toHaveBeenCalled();
  expect(screen.getByRole('button', { name: 'Dùng run này' })).toBeVisible();
  expect(screen.getByRole('link', { name: 'Chọn run từ lịch sử' })).toHaveAttribute('href', '/admin/mining-runs?tab=history');
});

test('validates the run form and verifies only after explicit submit', async () => {
  const read = jest.spyOn(comboInputApi, 'read').mockResolvedValue({ runId: id, snapshotId: id,
    publicationDigest: 'a'.repeat(64), patternCount: '40', dictionaryCount: '20',
    inputStatus: 'VERIFIED_PUBLISHED_INPUT', generationStatus: 'BLOCKED', blockers: ['BUSINESS_POLICY_UNCONFIRMED'] });
  open('/admin/combos');
  fireEvent.click(screen.getByRole('button', { name: 'Dùng run này' }));
  expect(read).not.toHaveBeenCalled();
  expect(screen.getByText('Nhập UUID Run ID hợp lệ.')).toBeVisible();
  fireEvent.change(screen.getByLabelText('Run ID nguồn để sinh Combo'), { target: { value: id } });
  expect(read).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Dùng run này' }));
  await screen.findByText(/Đã xác minh 40 Pattern/);
  expect(read).toHaveBeenCalledTimes(1);
  expect(read).toHaveBeenCalledWith(id, expect.any(AbortSignal));
});
test('verifies selected run and displays business blockers without an enabled generate action', async () => {
  const read = jest.spyOn(comboInputApi, 'read').mockResolvedValue({ runId: id, snapshotId: id,
    publicationDigest: 'a'.repeat(64), patternCount: '23', dictionaryCount: '20',
    inputStatus: 'VERIFIED_PUBLISHED_INPUT', generationStatus: 'BLOCKED', blockers: ['BUSINESS_POLICY_UNCONFIRMED'] });
  open('/admin/combos?runId=' + id);
  await screen.findByText(/Đã xác minh 23 Pattern/);
  expect(read).toHaveBeenCalledWith(id, expect.any(AbortSignal));
  expect(screen.getByText(/Cần xác nhận chính sách/)).toBeVisible();
  expect(screen.getByRole('link', { name: 'Xem Pattern nguồn' })).toHaveAttribute('href', '/admin/patterns?runId=' + id + '&view=published');
  expect(screen.queryByRole('button', { name: /Sinh Combo/ })).not.toBeInTheDocument();
});
test('a failed producer read cannot appear as a verified input', async () => {
  jest.spyOn(comboInputApi, 'read').mockRejectedValue(new WdmApiError('http', 409, 'INVALID_RESOURCE_STATE', 'Unverified publication'));
  open('/admin/combos?runId=' + id);
  await screen.findByRole('alert');
  expect(screen.queryByText(/Đã xác minh/)).not.toBeInTheDocument();
});
