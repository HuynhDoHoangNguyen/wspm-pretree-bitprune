import React, { useEffect, useMemo, useState } from 'react';
import { authSession, useSharedAuth } from 'auth/session';
import { getMiningRun, listMiningRuns } from '../mining-run/MiningRun.api';
import MiningMonitoring from './MiningMonitoring';
import type { MiningMonitoringSource } from './MiningMonitoring.types';

/** Production adapter: shared RUN API and session; no fixture or automatic refresh. */
export default function MiningMonitoringPage() {
  const { status, user, forbidden } = useSharedAuth();
  const [sessionScope, setSessionScope] = useState(0);
  // Also reset a batched logout/relogin of the same user without changing shared Auth.
  useEffect(() => authSession.onClear(() => setSessionScope(scope => scope + 1)), []);
  if (status !== 'authenticated' || forbidden || !user?.roles.includes('ADMIN')) return null;
  return <AuthenticatedMonitoring key={`${user.userId}:${sessionScope}`}/>;
}

function AuthenticatedMonitoring() {
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
