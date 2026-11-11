import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ComboAdmin from './ComboAdmin';
import { ComboClient } from './ComboClient';
jest.mock('./PublishedComboInputPanel', () => function GenerationPanel() { return <div>Tạo Combo từ mining</div>; });
function client(): jest.Mocked<ComboClient> {
  return { reviewReady: true, batches: jest.fn().mockResolvedValue({ page: 0, size: 20, total: '21',
    items: [{ batchId: 'B1', runId: 'R1', status: 'DRAFT', createdAt: '2026-11-08T10:00:00.000000Z', comboCount: '218', draftCount: '217', approvedCount: '1', rejectedCount: '0' }] }),
  list: jest.fn().mockImplementation((filters, page, size) => Promise.resolve({ page, size, scopeTotal: '0', filteredTotal: '0', appliedFilters: filters, items: [] })),
  detail: jest.fn(), review: jest.fn() };
}
test('loads only batch history first and opens one scoped batch lazily', async () => {
  const api = client(); render(<MemoryRouter><ComboAdmin client={api}/></MemoryRouter>);
  expect((await screen.findAllByText('218'))[0]).toBeInTheDocument(); expect(api.list).not.toHaveBeenCalled(); expect(api.detail).not.toHaveBeenCalled();
  expect(screen.getAllByText(/17:00:00/)[0]).toBeInTheDocument();
  fireEvent.click(screen.getAllByRole('button', { name: 'Mở batch B1' })[0]);
  await waitFor(() => expect(api.list).toHaveBeenCalledWith({ batchId: 'B1' }, 0, 20, expect.any(Object)));
  fireEvent.click(screen.getByRole('button', { name: 'Bỏ lọc' }));
  await waitFor(() => expect(api.list).toHaveBeenLastCalledWith({ batchId: 'B1' }, 0, 20, expect.any(Object)));
  fireEvent.click(screen.getByRole('button', { name: 'Lịch sử batch' }));
  expect((await screen.findAllByText('218'))[0]).toBeInTheDocument();
});
test('pages batches on server and separates generation from history', async () => {
  const api = client(); render(<MemoryRouter><ComboAdmin client={api}/></MemoryRouter>);
  await screen.findAllByText('218');
  (api.batches as jest.Mock).mockResolvedValue({ page: 1, size: 20, total: '21', items: [] });
  fireEvent.click(screen.getByRole('button', { name: 'Trang sau' }));
  await waitFor(() => expect(api.batches).toHaveBeenLastCalledWith(1, 20, expect.any(Object)));
  fireEvent.click(screen.getByRole('button', { name: 'Tạo đề xuất Combo' }));
  expect(await screen.findByText('Tạo Combo từ mining')).toBeInTheDocument(); expect(api.list).not.toHaveBeenCalled();
});
test('failed history can retry without loading combos', async () => {
  const api = client(); (api.batches as jest.Mock).mockRejectedValueOnce(new Error('offline'));
  render(<MemoryRouter><ComboAdmin client={api}/></MemoryRouter>);
  expect(await screen.findByRole('alert')).toHaveTextContent('Không tải được lịch sử');
  fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
  expect((await screen.findAllByText('218'))[0]).toBeInTheDocument(); expect(api.list).not.toHaveBeenCalled();
});
