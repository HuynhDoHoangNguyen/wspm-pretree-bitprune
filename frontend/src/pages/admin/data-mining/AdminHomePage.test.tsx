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
});
