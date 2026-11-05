// Test-only bundle, served by run.mjs. Never imported into the product bundle.
import React from 'react';
import ReactDOM from 'react-dom';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from 'react-query';
import AuthBoundary from '../../src/auth/AuthBoundary';
import { authSession } from '../../src/auth/session';
import { createAuthApi } from '../../src/auth/api';
import { wdmApi } from '../../src/api/admin/data-mining';

const queries = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const api = createAuthApi();
const exportPath = '/v1/admin/order-exports';
const query = { fromTime: '2026-11-01T00:00:00Z', toTime: '2026-12-01T00:00:00Z' };
async function download() {
  const result = await wdmApi.download(exportPath, { query });
  const url = URL.createObjectURL(result.blob), link = document.createElement('a');
  link.href = url; link.download = 'auth06-orders.csv'; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
const commands = {
  register: api.register,
  login: authSession.login,
  refresh: authSession.refresh,
  restore: authSession.restore,
  logout: authSession.logout,
  check: authSession.checkIdentity,
  download,
  read: async () => {
    try { await wdmApi.download(exportPath, { query }); return { status: 200 }; }
    catch (error) { return { status: (error as { status?: number }).status || 0 }; }
  },
  cache: () => queries.setQueryData('protected-fixture', 'test-value'),
  snapshot: () => {
    const { status, user, forbidden } = authSession.useStore.getState();
    return { status, user, forbidden, tokenPresent: !!authSession.token(), cacheCount: queries.getQueryCache().getAll().length };
  },
};
Object.assign(window, { auth06: commands });
function Harness() {
  const state = authSession.useStore();
  return <><output data-testid="status">{state.status}</output>
    <button onClick={() => void download().catch(() => undefined)}>Download fixture CSV</button>
    <button onClick={() => void authSession.logout().catch(() => undefined)}>Logout fixture</button></>;
}
ReactDOM.render(<QueryClientProvider client={queries}><MemoryRouter><AuthBoundary><Harness/></AuthBoundary></MemoryRouter></QueryClientProvider>, document.getElementById('root'));
