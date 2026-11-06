import React, { useMemo, useState } from 'react';
import { getMiningRun, listMiningRuns } from '../mining-run/MiningRun.api';
import MiningMonitoring from './MiningMonitoring';
import type { MiningMonitoringSource } from './MiningMonitoring.types';

/** Production adapter: shared RUN API and session; no fixture or automatic refresh. */
export default function MiningMonitoringPage() {
  const [observedAt, setObservedAt] = useState(() => Date.now());
  const source = useMemo<MiningMonitoringSource>(() => ({
    async list(params, signal) {
      const page = await listMiningRuns(params, signal);
      if (!signal?.aborted) setObservedAt(Date.now());
      return page;
    },
    detail: getMiningRun,
  }), []);
  return <MiningMonitoring source={source} observedAt={observedAt} manualRefresh/>;
}
