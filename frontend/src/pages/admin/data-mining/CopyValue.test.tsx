import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import CopyValue from './CopyValue';
it('copies the exact ID and offers manual copying if browser permission fails', async () => {
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false,
    addListener: jest.fn(), removeListener: jest.fn(), addEventListener: jest.fn(), removeEventListener: jest.fn() })) });
  const write = jest.fn().mockResolvedValue(undefined);
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: write } });
  render(<MantineProvider><CopyValue label="Snapshot ID" value="exact-id"/></MantineProvider>);
  fireEvent.click(screen.getByRole('button', { name: 'Sao chép Snapshot ID' }));
  expect(await screen.findByText('Đã sao chép.')).toBeVisible(); expect(write).toHaveBeenCalledWith('exact-id');
  write.mockRejectedValue(new Error('denied')); fireEvent.click(screen.getByRole('button', { name: 'Sao chép Snapshot ID' }));
  expect(await screen.findByText(/Chưa thể sao chép tự động/)).toBeVisible(); expect(screen.getByText('exact-id')).toBeVisible();
});
