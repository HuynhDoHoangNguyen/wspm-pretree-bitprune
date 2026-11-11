import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import ComboAdmin from './ComboAdmin';
import { ComboClient, unavailableComboClient } from './ComboClient';

jest.mock('./PublishedComboInputPanel', () => ({ __esModule: true, default: () => <p>Generation panel</p> }));

function Location() {
  const location = useLocation(), navigate = useNavigate();
  return <><output aria-label="URL">{location.search}</output><button onClick={() => navigate(-1)}>Back</button></>;
}

beforeEach(() => {
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() })) });
});

it('retains the batch page through detail, return, generation and browser back', async () => {
  const batches = jest.fn().mockImplementation((page: number) => Promise.resolve({ page, size: 20, total: '80', items: [{
    batchId: 'B1', runId: 'R1', status: 'DRAFT', createdAt: '2026-11-10T01:00:00Z', comboCount: '0', draftCount: '0', approvedCount: '0', rejectedCount: '0',
  }] }));
  const client: ComboClient = { ...unavailableComboClient, batches,
    list: jest.fn().mockImplementation(filters => Promise.resolve({ page: 0, size: 20, scopeTotal: '0', filteredTotal: '0', items: [], appliedFilters: filters })) };
  render(<MantineProvider><MemoryRouter initialEntries={['/admin/combos?batchPage=2']}><Location/><ComboAdmin client={client}/></MemoryRouter></MantineProvider>);
  await waitFor(() => expect(batches).toHaveBeenLastCalledWith(2, 20, expect.anything()));
  fireEvent.click((await screen.findAllByRole('button', { name: 'Mở batch B1' }))[0]);
  expect(screen.getByLabelText('URL')).toHaveTextContent('batchPage=2&batchId=B1');
  fireEvent.click(screen.getByRole('button', { name: 'Lịch sử batch' }));
  await screen.findAllByRole('button', { name: 'Mở batch B1' });
  expect(screen.getByLabelText('URL')).toHaveTextContent('?batchPage=2');
  fireEvent.click(screen.getByRole('button', { name: 'Trang sau' }));
  await waitFor(() => expect(batches).toHaveBeenLastCalledWith(3, 20, expect.anything()));
  fireEvent.click(screen.getByRole('button', { name: 'Tạo đề xuất Combo' }));
  expect(screen.getByText('Generation panel')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Back' }));
  await screen.findAllByRole('button', { name: 'Mở batch B1' });
  expect(screen.getByLabelText('URL')).toHaveTextContent('?batchPage=3');
});
