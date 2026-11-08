import React from 'react';
import { matchPath, Route } from 'react-router-dom';
import { Database, ListSearch, PlayerPlay } from 'tabler-icons-react';
import ManagerPath from 'constants/ManagerPath';
import DataMiningGuard from './DataMiningGuard';
import DatasetsPage from './DatasetsPage';
import MiningRunsPage from './MiningRunsPage';
import PatternsPage from './PatternsPage';
import SnapshotDetailPage from './SnapshotDetailPage';
import MiningRunDetailPage from './MiningRunDetailPage';
import PatternDetailPage from './PatternDetailPage';

export const dataMiningPages = [
  { path: ManagerPath.DATASETS, label: 'Dữ liệu khai phá', Component: DatasetsPage, Icon: Database },
  { path: ManagerPath.MINING_RUNS, label: 'Lần chạy khai phá', Component: MiningRunsPage, Icon: PlayerPlay },
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
  )), <Route key="snapshot-detail" path={ManagerPath.DATASETS + '/:snapshotId'} element={(
    <DataMiningGuard><SnapshotDetailPage/></DataMiningGuard>
  )}/>, <Route key="mining-run-detail" path={ManagerPath.MINING_RUNS + '/:runId'} element={(
    <DataMiningGuard><MiningRunDetailPage/></DataMiningGuard>
  )}/>, <Route key="pattern-detail" path={ManagerPath.PATTERNS + '/:runId/:patternId'} element={(
    <DataMiningGuard><PatternDetailPage/></DataMiningGuard>
  )}/>];
}
