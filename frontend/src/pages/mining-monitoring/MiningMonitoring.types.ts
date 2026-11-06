import type { MiningRunDataSource } from '../mining-run/MiningRunHistory';

// Existing list/detail boundary; no new backend endpoint or realtime contract.
export type MiningMonitoringSource = MiningRunDataSource;

export interface MiningMonitoringProps {
  source: MiningMonitoringSource;
  observedAt: number;
  manualRefresh?: boolean;
  // Preview supplies its own destination, or null when history preview is disabled.
  historyHref?: string | null;
}
