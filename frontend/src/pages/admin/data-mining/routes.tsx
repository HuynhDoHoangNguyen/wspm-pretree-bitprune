import React from 'react';
import { matchPath, Route } from 'react-router-dom';
import { Activity, ChartBar, Database, History, ListSearch, PlayerPlay } from 'tabler-icons-react';
import ManagerPath from 'constants/ManagerPath';
import DataMiningGuard from './DataMiningGuard';
import DatasetsPage from './DatasetsPage';
import MiningRunsPage from './MiningRunsPage';
import PatternExplorer from 'pages/pattern/PatternExplorer';
import PatternsPage from './PatternsPage';
import SnapshotDetailPage from './SnapshotDetailPage';
import MiningRunHistory from 'pages/admin/data-mining/mining-run/MiningRunHistory';
import MiningMonitoringPage from 'pages/admin/data-mining/mining-monitoring/MiningMonitoringPage';
import MiningComparisonPage from 'pages/admin/data-mining/mining-comparison/MiningComparisonPage';
import MiningRunDetailPage from './MiningRunDetailPage';
import PatternDetailPage from './PatternDetailPage';

export const dataMiningPages = [
  { path: ManagerPath.DATASETS, label: 'Dữ liệu khai phá', Component: DatasetsPage, Icon: Database },
  { path: ManagerPath.MINING_RUNS, label: 'Lần chạy khai phá', Component: MiningRunsPage, Icon: PlayerPlay },
  { path: '/admin/mining-run-history', label: 'Lịch sử Mining RUN', Component: MiningRunHistory, Icon: History },
  { path: '/admin/mining-monitoring', label: 'Giám sát Mining', Component: MiningMonitoringPage, Icon: Activity },
  { path: '/admin/mining-comparison', label: 'So sánh thuật toán', Component: MiningComparisonPage, Icon: ChartBar },
  { path: ManagerPath.PATTERNS, label: 'Mẫu khai phá', Component: PatternsPage, Icon: ListSearch },
];

export function isDataMiningPath(pathname: string): boolean {
  return dataMiningPages.some(({ path }) => matchPath({ path, end: false }, pathname) !== null);
}

/** Return actual Route elements so React Router and tests consume the same configuration. */
export function createDataMiningRoutes() {
  return [...dataMiningPages.map(({ path, Component }) => (
    <Route key={path} path={path} element={(
      <DataMiningGuard>
        <Component/>
      </DataMiningGuard>
    )}/>
  )), <Route key="pattern-explorer" path="/admin/pattern-explorer" element={(
    <DataMiningGuard><PatternExplorer/></DataMiningGuard>
  )}/>, <Route key="snapshot-detail" path={ManagerPath.DATASETS + '/:snapshotId'} element={(
    <DataMiningGuard><SnapshotDetailPage/></DataMiningGuard>
  )}/>, <Route key="mining-run-detail" path={ManagerPath.MINING_RUNS + '/:runId'} element={(
    <DataMiningGuard><MiningRunDetailPage/></DataMiningGuard>
  )}/>, <Route key="pattern-detail" path={ManagerPath.PATTERNS + '/:runId/:patternId'} element={(
    <DataMiningGuard><PatternDetailPage/></DataMiningGuard>
  )}/>];
}
