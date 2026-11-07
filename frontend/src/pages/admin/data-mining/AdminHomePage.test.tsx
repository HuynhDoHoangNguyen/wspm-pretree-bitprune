import React from 'react';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { MantineProvider } from '@mantine/core';
import AdminHomePage from './AdminHomePage';
it('guides seven steps with real data links and clearly unavailable later stages', () => {
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false,
    addListener: jest.fn(), removeListener: jest.fn(), addEventListener: jest.fn(), removeEventListener: jest.fn() })) });
  render(<MemoryRouter><MantineProvider><AdminHomePage/></MantineProvider></MemoryRouter>);
  expect(screen.getByRole('link', { name: 'Bắt đầu với dữ liệu' })).toHaveAttribute('href', '/admin/datasets');
  expect(screen.getAllByRole('region', { name: /^Bước/ })).toHaveLength(7);
  const combo = screen.getByRole('region', { name: 'Bước 7: Tạo combo' });
  expect(within(combo).getByText('Chưa khả dụng')).toBeVisible(); expect(within(combo).queryByRole('link')).not.toBeInTheDocument();
  const mining = screen.getByRole('region', { name: 'Bước 5: Chạy khai phá' });
  expect(within(mining).queryByText('Chưa khả dụng')).not.toBeInTheDocument();
  expect(within(mining).getByRole('link', { name: 'Mở trang khai phá' })).toHaveAttribute('href', '/admin/mining-runs');
  expect(within(mining).getByRole('link', { name: 'Xem lịch sử run' })).toHaveAttribute('href', '/admin/mining-runs?tab=history');
  expect(within(screen.getByRole('region', { name: 'Bước 6: Xem mẫu sản phẩm' })).getByText('Chưa khả dụng')).toBeVisible();
});
