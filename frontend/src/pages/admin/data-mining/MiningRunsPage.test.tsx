import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { useSharedAuth } from 'auth/session';
import { miningApi } from 'api/admin/data-mining';
import MiningRunsPage from './MiningRunsPage';
jest.mock('auth/session', () => ({ useSharedAuth: jest.fn() }));
const id = '11111111-2222-4333-8444-555555555555';
beforeEach(() => {
  (useSharedAuth as unknown as jest.Mock).mockReturnValue({ user: { userId: 'admin', roles: ['ADMIN'] }, status: 'authenticated', forbidden: false });
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() })) });
});
afterEach(() => jest.restoreAllMocks());
it.each(['?snapshotId=' + id, '?snapshotId=bad', '?snapshotId=' + id + '&snapshotId=other'])('prefills untrusted query without sending POST: %s', query => {
  const preview = jest.spyOn(miningApi, 'preview'), validate = jest.spyOn(miningApi, 'validate');
  render(<MantineProvider><MemoryRouter initialEntries={['/admin/mining-runs' + query]}><MiningRunsPage/></MemoryRouter></MantineProvider>);
  expect(screen.getByLabelText(/Snapshot ID/)).toHaveValue(query.includes('&') ? '' : query.endsWith('bad') ? 'bad' : id);
  expect(preview).not.toHaveBeenCalled(); expect(validate).not.toHaveBeenCalled();
});
it('resets form input when query selection changes', () => {
  function Jump() { const navigate = useNavigate(); return <button onClick={() => navigate('/admin/mining-runs?snapshotId=other')}>Đổi snapshot</button>; }
  render(<MantineProvider><MemoryRouter initialEntries={['/admin/mining-runs?snapshotId=' + id]}><Jump/><MiningRunsPage/></MemoryRouter></MantineProvider>);
  fireEvent.change(screen.getByLabelText(/Giá trị minsup/), { target: { value: '14' } }); fireEvent.click(screen.getByText('Đổi snapshot'));
  expect(screen.getByLabelText(/Snapshot ID/)).toHaveValue('other'); expect(screen.getByLabelText(/Giá trị minsup/)).toHaveValue('');
});
