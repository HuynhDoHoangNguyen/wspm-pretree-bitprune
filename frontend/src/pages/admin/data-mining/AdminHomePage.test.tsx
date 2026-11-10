import React from 'react';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { MantineProvider } from '@mantine/core';
import AdminHomePage from './AdminHomePage';
it('guides execution and persisted Pattern/Combo without promising automatic publication', () => {
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false,
    addListener: jest.fn(), removeListener: jest.fn(), addEventListener: jest.fn(), removeEventListener: jest.fn() })) });
  render(<MemoryRouter><MantineProvider><AdminHomePage/></MantineProvider></MemoryRouter>);
  expect(screen.getByRole('link', { name: 'Bắt đầu với dữ liệu' })).toHaveAttribute('href', '/admin/datasets');
  expect(screen.getAllByRole('region', { name: /^Bước/ })).toHaveLength(7);
  const config = screen.getByRole('region', { name: 'Bước 5: Chạy khai phá' });
  expect(within(config).getByRole('link', { name: 'Mở trang khai phá' })).toHaveAttribute('href', '/admin/mining-runs');
  expect(within(config).getByRole('link', { name: 'Xem lịch sử run' })).toHaveAttribute('href', '/admin/mining-runs?tab=history');
  expect(screen.getByRole('link', { name: 'Mở Pattern Explorer' })).toHaveAttribute('href', '/admin/patterns');
  const combo = screen.getByRole('region', { name: 'Bước 7: Xem & review Combo đã lưu' });
  expect(within(combo).getByText('Cần kết quả đã lưu')).toBeVisible();
  expect(within(combo).getByRole('link')).toHaveAttribute('href', '/admin/combos');
  expect(screen.getByText(/có thống kê và tệp kết quả được máy chủ xác minh/)).toBeVisible();
  expect(screen.getByText(/Preview\/Validate không tự chuyển đổi/)).toBeVisible();
  expect(screen.queryByRole('button', { name: /Chạy khai phá|Tạo combo/i })).not.toBeInTheDocument();
});
