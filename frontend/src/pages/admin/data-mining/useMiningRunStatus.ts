import { useCallback, useEffect, useRef, useState } from 'react';
import { useSharedAuth } from 'auth/session';
import { miningRunApi, MiningRunStatus, WdmApiError } from 'api/admin/data-mining';
import { isRunId } from 'api/admin/data-mining/mining-run-response';
import { hasDataMiningAdminAccess } from './access';
import { boundedRunRequest, runRequestError, RUN_GET_TIMEOUT, RUN_POLL_BUDGET, RUN_POLL_DELAY } from './mining-run-lifecycle';

interface State { binding: string; report: MiningRunStatus | null; busy: boolean; paused: boolean; error: WdmApiError | null; lastReadAt: string | null }
const running = (report: MiningRunStatus | null) => !!report && ['QUEUED', 'RUNNING'].includes(report.status);
export function useMiningRunStatus(runId: string) {
  const auth = useSharedAuth();
  const binding = JSON.stringify([runId, auth.user?.userId, auth.user?.roles, auth.status, auth.forbidden, auth.accessToken]);
  const allowed = auth.status === 'authenticated' && !auth.forbidden && hasDataMiningAdminAccess(auth.user);
  const latest = useRef(binding); latest.current = binding;
  const active = useRef<AbortController | null>(null), deadline = useRef(0), stopped = useRef(false);
  const initial = (): State => ({ binding, report: null, busy: false, paused: false, error: null, lastReadAt: null });
  const [state, setState] = useState<State>(initial);
  const visible = state.binding === binding && allowed ? state : initial();
  const report = useRef(visible.report); report.current = visible.report;
  const [hidden, setHidden] = useState(document.hidden);
  const read = useCallback(async (manual = false) => {
    if (!allowed || active.current || latest.current !== binding || document.hidden || (!manual && stopped.current)) return;
    if (!isRunId(runId)) {
      stopped.current = true;
      setState({ binding, report: null, busy: false, paused: true, error: new WdmApiError('http', 400, 'VALIDATION_FAILED', 'Invalid run ID.'), lastReadAt: null }); return;
    }
    if (manual) { deadline.current = Date.now() + RUN_POLL_BUDGET; stopped.current = false; }
    const controller = new AbortController(), ticket = binding; active.current = controller;
    setState(previous => ({ ...previous, binding, busy: true, paused: false, error: null }));
    try {
      const next = await boundedRunRequest(() => miningRunApi.read(runId, controller.signal), controller, RUN_GET_TIMEOUT);
      if (latest.current !== ticket || active.current !== controller || document.hidden) return;
      setState({ binding, report: next, busy: false, paused: false, error: null, lastReadAt: new Date().toISOString() });
    } catch (failure) {
      if (latest.current !== ticket || active.current !== controller || document.hidden) return;
      stopped.current = true;
      const error = runRequestError(failure), denied = error.status === 401 || error.status === 403;
      setState(previous => ({ ...previous, binding, report: denied ? null : previous.report, lastReadAt: denied ? null : previous.lastReadAt,
        busy: false, paused: true, error }));
    } finally { if (active.current === controller) active.current = null; }
  }, [allowed, binding, runId]);
  useEffect(() => {
    latest.current = binding; deadline.current = Date.now() + RUN_POLL_BUDGET; stopped.current = false;
    setState({ binding, report: null, busy: false, paused: false, error: null, lastReadAt: null });
    void read();
    return () => { latest.current = ''; active.current?.abort(); active.current = null; };
  }, [binding, read]);
  useEffect(() => {
    function visibility() {
      setHidden(document.hidden);
      if (document.hidden) {
        active.current?.abort(); active.current = null;
        setState(previous => ({ ...previous, busy: false }));
      } else if (deadline.current > Date.now() && !stopped.current && (!report.current || running(report.current))) void read();
      else if (!report.current || running(report.current)) setState(previous => ({ ...previous, paused: true }));
    }
    document.addEventListener('visibilitychange', visibility);
    return () => document.removeEventListener('visibilitychange', visibility);
  }, [read]);
  useEffect(() => {
    if (!allowed || hidden || visible.busy || visible.paused || !running(visible.report)) return;
    const timer = window.setTimeout(() => {
      if (Date.now() >= deadline.current) { stopped.current = true; setState(previous => ({ ...previous, paused: true })); }
      else void read();
    }, RUN_POLL_DELAY);
    return () => window.clearTimeout(timer);
  }, [allowed, hidden, visible.busy, visible.paused, visible.report, read]);
  return { ...visible, hidden, read: () => read(true) };
}
