import React, { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Button, ColorScheme, ColorSchemeProvider, MantineProvider } from '@mantine/core';
import createCache from '@emotion/cache';
import { CustomerStoreLayout } from './CustomerCombos';

jest.mock('@emotion/cache', () => ({ __esModule: true, default: jest.fn(jest.requireActual('@emotion/cache').default) }));
const createCacheMock = createCache as jest.MockedFunction<typeof createCache>;
beforeEach(() => {
  createCacheMock.mockImplementation(jest.requireActual('@emotion/cache').default);
  Object.defineProperty(window, 'ResizeObserver', { configurable: true,
    value: class { observe() { return undefined; } unobserve() { return undefined; } disconnect() { return undefined; } },
  });
});

function Store() {
  const [scheme, setScheme] = useState<ColorScheme>('light');
  const [version, setVersion] = useState(0);
  return <MemoryRouter><ColorSchemeProvider colorScheme={scheme} toggleColorScheme={value => setScheme(value || 'light')}>
    <MantineProvider theme={{ colorScheme: scheme }}>
      <CustomerStoreLayout><Button onClick={() => setVersion(value => value + 1)}>Render {version}</Button>
        {Array.from({ length: 20 }, (_, i) => <Button key={i}>Item {i}</Button>)}
      </CustomerStoreLayout>
    </MantineProvider>
  </ColorSchemeProvider></MemoryRouter>;
}

test('Customer uses a bounded CSS cache for many components, rerenders and theme changes', () => {
  render(<Store/>);
  expect(screen.getAllByRole('button', { name: /^Item / })).toHaveLength(20);
  expect(createCacheMock.mock.calls.length).toBeLessThanOrEqual(2);
  const allocations = createCacheMock.mock.calls.length;
  fireEvent.click(screen.getByRole('button', { name: 'Render 0' }));
  fireEvent.click(screen.getByRole('radio', { name: 'Tối' }));
  expect(screen.getByTestId('customer-store-layout')).toHaveAttribute('data-color-scheme', 'dark');
  fireEvent.click(screen.getByRole('radio', { name: 'Sáng' }));
  expect(screen.getByTestId('customer-store-layout')).toHaveAttribute('data-color-scheme', 'light');
  expect(createCacheMock.mock.calls.length).toBe(allocations);
  expect(screen.getByRole('banner')).toBeInTheDocument();
  expect(screen.getByRole('contentinfo')).toBeInTheDocument();
});
