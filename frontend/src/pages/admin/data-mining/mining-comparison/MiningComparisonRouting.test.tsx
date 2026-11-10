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
  list.mockReset().mockResolvedValue({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
  detail.mockReset();
});
afterEach(() => { clients.forEach(client => client.clear()); clients.length = 0; authSession.unauthorized(); });
afterAll(() => setLogger(console));

function ComparisonButtons() {
  const navigate = useNavigate();
  return <><button onClick={() => navigate(-1)}>Back</button><button onClick={() => navigate('/admin/mining-run-history')}>Open legacy history</button><button onClick={() => navigate(1)}>Forward</button></>;
}
function show(entries: string[]) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, cacheTime: 0 } } });
  clients.push(client);
  return render(<QueryClientProvider client={client}><MantineProvider>
    <MemoryRouter initialEntries={entries} initialIndex={entries.length - 1}>
      <ComparisonButtons/>
      <Routes><Route path="/admin" element={<Admin/>}>{createDataMiningRoutes()}</Route></Routes>
    </MemoryRouter>
  </MantineProvider></QueryClientProvider>);
}
function expectActive(label: string) {
  const nav = screen.getByRole('navigation', { name: 'Dữ liệu & Khai phá' });
  expect(within(nav).getByRole('link', { name: label })).toHaveAttribute('aria-current', 'page');
  expect(within(nav).getAllByRole('link').filter(link => link.getAttribute('aria-current') === 'page')).toHaveLength(1);
}

test.each(['/admin/mining-comparison', '/ADMIN/MINING-COMPARISON/?page=2#detail'])(
  'direct Comparison URL %s uses the shared Admin shell and exactly one active link', async url => {
    show([url]);
    expect(await screen.findByText('Chưa có RUN để so sánh.')).toBeVisible();
    expect(screen.getByRole('banner')).toHaveTextContent('Shared Admin header');
    expectActive('So sánh thuật toán');
    expect(screen.getByRole('link', { name: 'Tạo lần chạy', hidden: true })).not.toHaveAttribute('aria-current');
    expect(list).toHaveBeenCalledWith({ page: 0, size: 100, algorithm: '', status: '' }, expect.any(AbortSignal));
  }
);
test('configuration route and snapshot query survive navigation to Comparison and back/forward', async () => {
  show(['/admin/mining-runs?snapshotId=existing-snapshot']);
  expect(screen.getByRole('form', { name: 'Existing mining configuration' })).toHaveTextContent('existing-snapshot');
  expect(list).not.toHaveBeenCalled();
  expectActive('Tạo lần chạy');
  fireEvent.click(screen.getByRole('link', { name: 'So sánh thuật toán' }));
  await screen.findByText('Chưa có RUN để so sánh.');
  expectActive('So sánh thuật toán');
  fireEvent.click(screen.getByRole('button', { name: 'Back' }));
  expect(screen.getByRole('form', { name: 'Existing mining configuration' })).toHaveTextContent('existing-snapshot');
  expectActive('Tạo lần chạy');
  fireEvent.click(screen.getByRole('button', { name: 'Forward' }));
  expect(await screen.findByText('Chưa có RUN để so sánh.')).toBeVisible();
  expectActive('So sánh thuật toán');
});
test('Comparison API error retains the Admin shell and retry recovers', async () => {
  list.mockRejectedValueOnce(new MiningRunApiError(503));
  show(['/admin/mining-comparison']);
  fireEvent.click(await screen.findByRole('button', { name: 'Thử lại' }));
  expect(await screen.findByText('Chưa có RUN để so sánh.')).toBeVisible();
  expect(screen.getByRole('banner')).toBeVisible();
});
test('non-ADMIN cannot render Comparison or trigger API reads', () => {
  authSession.useStore.setState({ user: { ...admin, roles: ['CUSTOMER'] } });
  show(['/admin/mining-comparison']);
  expect(screen.getByRole('alert')).toHaveTextContent('không có quyền ADMIN');
  expect(list).not.toHaveBeenCalled();
  expect(detail).not.toHaveBeenCalled();
});

test('Comparison links to the real History route while preserving the configuration destination', async () => {
  show(['/admin/mining-comparison']);
  await screen.findByText('Chưa có RUN để so sánh.');
  fireEvent.click(screen.getByRole('button', { name: 'Open legacy history' }));
  expect(await screen.findByText('Không có RUN phù hợp.')).toBeVisible();
  expectActive('Lịch sử lần chạy');
  fireEvent.click(screen.getByRole('link', { name: 'Tạo lần chạy', hidden: true }));
  expect(screen.getByRole('form', { name: 'Existing mining configuration' })).toBeVisible();
  expectActive('Tạo lần chạy');
});

test('Monitoring remains a separate destination from Comparison', async () => {
  show(['/admin/mining-comparison']);
  await screen.findByText('Chưa có RUN để so sánh.');
  fireEvent.click(screen.getByRole('button', { name: 'Lần chạy khai phá' }));
  fireEvent.click(screen.getByRole('link', { name: 'Giám sát thực thi' }));
  expect(await screen.findByText('Chưa có RUN để theo dõi.')).toBeVisible();
  expectActive('Giám sát thực thi');
  expect(screen.queryByText('Chưa có RUN để so sánh.')).not.toBeInTheDocument();
});
