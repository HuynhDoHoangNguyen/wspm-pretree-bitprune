import React from 'react';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { QueryClient, QueryClientProvider, setLogger } from 'react-query';
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom';
import Admin from 'pages/Admin';
import { createDataMiningRoutes } from 'pages/admin/data-mining/routes';
import { authSession } from 'auth/session';
import { getMiningRun, listMiningRuns, MiningRunApiError } from '../../../../api/admin/data-mining/history/MiningRun.api';

jest.mock('../../../../api/admin/data-mining/history/MiningRun.api', () => ({
  ...jest.requireActual('../../../../api/admin/data-mining/history/MiningRun.api'), listMiningRuns: jest.fn(), getMiningRun: jest.fn(),
}));
jest.mock('components/DefaultHeader/DefaultHeader', () => ({
  DefaultHeader: () => <header>Shared Admin header</header>,
}));
jest.mock('components/DefaultNavbar/DefaultNavbar', () => ({
  DefaultNavbar: jest.requireActual('pages/admin/data-mining/DataMiningNavigation').default,
}));
jest.mock('pages/admin-signin', () => ({ __esModule: true, default: () => <p>Admin sign in</p> }));
jest.mock('auth/useSignOut', () => ({ useSignOut: () => jest.fn() }));
jest.mock('pages/admin/data-mining/MiningConfigForm', () => ({
  __esModule: true,
  default: ({ initialSnapshotId }: { initialSnapshotId: string }) => (
    <form aria-label="Existing mining configuration"><output>{initialSnapshotId}</output></form>
  ),
}));

const list = listMiningRuns as jest.MockedFunction<typeof listMiningRuns>;
const detail = getMiningRun as jest.MockedFunction<typeof getMiningRun>;
const clients: QueryClient[] = [];
const admin = { userId: 'admin', username: 'unit', displayName: 'Admin', email: null,
  emailVerified: false, status: 'ACTIVE' as const, roles: ['ADMIN'] };

beforeAll(() => {
  setLogger({ log: () => undefined, warn: () => undefined, error: () => undefined });
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: (query: string) => ({
    matches: false, media: query, onchange: null, addListener: jest.fn(), removeListener: jest.fn(),
    addEventListener: jest.fn(), removeEventListener: jest.fn(), dispatchEvent: jest.fn(),
  }) });
  Object.defineProperty(window, 'ResizeObserver', { configurable: true, value: class {
    observe = () => undefined;
    unobserve = () => undefined;
    disconnect = () => undefined;
  } });
});
beforeEach(() => {
  authSession.unauthorized();
  authSession.useStore.setState({ status: 'authenticated', user: admin, accessToken: 'unit-token', forbidden: false, error: null });
  list.mockReset().mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  detail.mockReset();
});
afterEach(() => { clients.forEach(client => client.clear()); clients.length = 0; authSession.unauthorized(); });
afterAll(() => setLogger(console));

function MonitoringButtons() {
  const navigate = useNavigate();
  return <><button onClick={() => navigate(-1)}>Back</button><button onClick={() => navigate(1)}>Forward</button></>;
}
function show(entries: string[]) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, cacheTime: 0 } } });
  clients.push(client);
  return render(<QueryClientProvider client={client}><MantineProvider>
    <MemoryRouter initialEntries={entries} initialIndex={entries.length - 1}>
      <MonitoringButtons/>
      <Routes><Route path="/admin" element={<Admin/>}>{createDataMiningRoutes()}</Route></Routes>
    </MemoryRouter>
  </MantineProvider></QueryClientProvider>);
}
function expectActive(label: string) {
  const nav = screen.getByRole('navigation', { name: 'Dữ liệu & Khai phá' });
  expect(within(nav).getByRole('link', { name: label })).toHaveAttribute('aria-current', 'page');
  expect(within(nav).getAllByRole('link').filter(link => link.getAttribute('aria-current') === 'page')).toHaveLength(1);
}

test.each(['/admin/mining-monitoring', '/ADMIN/MINING-MONITORING/?page=2#detail'])(
  'direct Monitoring URL %s uses the shared Admin shell and exactly one active link', async url => {
    show([url]);
    expect(await screen.findByText('Chưa có RUN để theo dõi.')).toBeVisible();
    expect(screen.getByRole('banner')).toHaveTextContent('Shared Admin header');
    expectActive('Giám sát thực thi');
    expect(screen.getByRole('link', { name: 'Tạo lần chạy', hidden: true })).not.toHaveAttribute('aria-current');
    expect(list).toHaveBeenCalledWith({ page: 0, size: 20, algorithm: '', status: '' }, expect.any(AbortSignal));
  }
);
test('configuration route and snapshot query survive navigation to Monitoring and back/forward', async () => {
  show(['/admin/mining-runs?snapshotId=existing-snapshot']);
  expect(screen.getByRole('form', { name: 'Existing mining configuration' })).toHaveTextContent('existing-snapshot');
  expect(list).not.toHaveBeenCalled();
  expectActive('Tạo lần chạy');
  fireEvent.click(screen.getByRole('link', { name: 'Giám sát thực thi' }));
  await screen.findByText('Chưa có RUN để theo dõi.');
  expectActive('Giám sát thực thi');
  fireEvent.click(screen.getByRole('button', { name: 'Back' }));
  expect(screen.getByRole('form', { name: 'Existing mining configuration' })).toHaveTextContent('existing-snapshot');
  expectActive('Tạo lần chạy');
  fireEvent.click(screen.getByRole('button', { name: 'Forward' }));
  expect(await screen.findByText('Chưa có RUN để theo dõi.')).toBeVisible();
  expectActive('Giám sát thực thi');
});
test('Monitoring API error retains the Admin shell and retry recovers', async () => {
  list.mockRejectedValueOnce(new MiningRunApiError(503));
  show(['/admin/mining-monitoring']);
  fireEvent.click(await screen.findByRole('button', { name: 'Thử lại' }));
  expect(await screen.findByText('Chưa có RUN để theo dõi.')).toBeVisible();
  expect(screen.getByRole('banner')).toBeVisible();
});
test('non-ADMIN cannot render Monitoring or trigger API reads', () => {
  authSession.useStore.setState({ user: { ...admin, roles: ['CUSTOMER'] } });
  show(['/admin/mining-monitoring']);
  expect(screen.getByRole('alert')).toHaveTextContent('không có quyền ADMIN');
  expect(list).not.toHaveBeenCalled();
  expect(detail).not.toHaveBeenCalled();
});

test('Monitoring links to the real History route while preserving the configuration destination', async () => {
  show(['/admin/mining-monitoring']);
  await screen.findByText('Chưa có RUN để theo dõi.');
  const historyLink = screen.getByRole('link', { name: 'Xem lịch sử RUN' });
  expect(historyLink).toHaveAttribute('href', '/admin/mining-run-history');
  fireEvent.click(historyLink);
  expect(await screen.findByText('Không có RUN phù hợp.')).toBeVisible();
  expectActive('Lịch sử lần chạy');
  fireEvent.click(screen.getByRole('link', { name: 'Tạo lần chạy', hidden: true }));
  expect(screen.getByRole('form', { name: 'Existing mining configuration' })).toBeVisible();
  expectActive('Tạo lần chạy');
});
