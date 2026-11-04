import React from 'react';
import { runInThisContext } from 'vm';
import { act, render, screen } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { QueryClient, QueryClientProvider } from 'react-query';
import AuthBoundary from './AuthBoundary';
import { authSession } from './session';
import DataMiningGuard from 'pages/admin/data-mining/DataMiningGuard';
import { wdmApi } from 'api/admin/data-mining';
import useLegacyCart from 'stores/use-auth-store';

beforeEach(() => {
  Object.assign(globalThis, { Headers: runInThisContext('Headers') });
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: jest.fn(() => ({ matches: false,
    addListener: jest.fn(), removeListener: jest.fn(), addEventListener: jest.fn(), removeEventListener: jest.fn() })) });
  jest.spyOn(authSession, 'bootstrap').mockResolvedValue();
  authSession.useStore.setState({ status: 'authenticated', accessToken: 'memory-token', forbidden: false, error: null,
    user: { userId: 'opaque-id', username: 'admin.test', email: null, displayName: 'Admin', roles: ['ADMIN'], status: 'ACTIVE', emailVerified: false } });
});
afterEach(() => { jest.restoreAllMocks(); authSession.stop(); delete process.env.REACT_APP_WDM_API_BASE_URL; });
test.each([401, 403])('WDM %i preserves original denial while clearing cache, aborting work and hiding private content', async status => {
  const query = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  query.setQueryData(['private-fixture'], 'private data');
  useLegacyCart.getState().updateCurrentCartId(42);
  const code = status === 401 ? 'AUTHENTICATION_REQUIRED' : 'ACCESS_DENIED';
  const fetcher = jest.fn().mockResolvedValue({ ok: false, status,
    headers: new Headers({ 'Content-Type': 'application/json', 'X-Request-Id': 'request-id' }),
    text: async () => JSON.stringify({ status, code, message: 'Denied', path: '/api/v1/admin/mining-runs', requestId: 'request-id', details: [] }) });
  Object.defineProperty(globalThis, 'fetch', { configurable: true, value: fetcher });
  const view = render(<MantineProvider><QueryClientProvider client={query}><AuthBoundary>
    <DataMiningGuard><p>Private fixture</p></DataMiningGuard>
  </AuthBoundary></QueryClientProvider></MantineProvider>);
  expect(screen.getByText('Private fixture')).toBeInTheDocument(); const signal = authSession.signal();
  await act(async () => { await expect(wdmApi.get('/v1/admin/mining-runs')).rejects.toMatchObject({ status, code }); });
  expect(signal.aborted).toBe(true); expect(query.getQueryData(['private-fixture'])).toBeUndefined();
  expect(useLegacyCart.getState().currentCartId).toBeNull();
  expect(screen.queryByText('Private fixture')).not.toBeInTheDocument();
  expect(fetcher).toHaveBeenCalledTimes(1);
  expect(authSession.useStore.getState().status).toBe(status === 401 ? 'anonymous' : 'authenticated');
  view.unmount();
});
test('foreign-origin Auth/WDM configuration fails closed without sending credentials', () => {
  process.env.REACT_APP_WDM_API_BASE_URL = 'https://other.example/api';
  const query = new QueryClient();
  render(<MantineProvider><QueryClientProvider client={query}><AuthBoundary><p>Private fixture</p></AuthBoundary></QueryClientProvider></MantineProvider>);
  expect(screen.getByRole('alert')).toHaveTextContent('cùng origin');
  expect(screen.queryByText('Private fixture')).not.toBeInTheDocument();
  expect(authSession.bootstrap).not.toHaveBeenCalled();
});
