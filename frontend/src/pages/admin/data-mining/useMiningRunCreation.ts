import { useEffect, useRef, useState } from 'react';
import { useSharedAuth } from 'auth/session';
import { miningRunApi, MiningRequest, MiningValidation, MiningRunAccepted, WdmApiError } from 'api/admin/data-mining';
import { hasDataMiningAdminAccess } from './access';
import { boundedRunRequest, runRequestError, RUN_POST_TIMEOUT } from './mining-run-lifecycle';

export type CreationPhase = 'ready' | 'submitting' | 'accepted' | 'rejected' | 'uncertain';
interface State { binding: string; phase: CreationPhase; submitted: MiningRequest | null; error: WdmApiError | null }
export function useMiningRunCreation(validation: MiningValidation | null, onAccepted?: (result: MiningRunAccepted) => void) {
  const auth = useSharedAuth();
  const binding = JSON.stringify([auth.user?.userId, auth.user?.roles, auth.status, auth.forbidden, auth.accessToken]);
  const latest = useRef(binding); latest.current = binding;
  const active = useRef<AbortController | null>(null), consumed = useRef(false);
  const allowed = auth.status === 'authenticated' && !auth.forbidden && hasDataMiningAdminAccess(auth.user);
  const blank = (): State => ({ binding, phase: 'ready', submitted: null, error: null });
  const [state, setState] = useState<State>(blank);
  const visible = state.binding === binding && allowed ? state : blank();
  useEffect(() => {
    latest.current = binding; consumed.current = false; setState({ binding, phase: 'ready', submitted: null, error: null });
    return () => { latest.current = ''; active.current?.abort(); active.current = null; };
  }, [binding]);
  async function create(input: MiningRequest) {
    if (!allowed || consumed.current || active.current || latest.current !== binding || !validation
      || validation.configurationStatus !== 'SUPPORTED' || validation.snapshotId !== input.snapshotId || validation.algorithm !== input.algorithm
      || validation.minimumSupport.mode !== input.minsupMode || validation.minimumSupport.configuredValue !== input.minsupValue) return;
    const submitted = { snapshotId: input.snapshotId, algorithm: input.algorithm, minsupMode: input.minsupMode, minsupValue: input.minsupValue };
    const controller = new AbortController(), ticket = binding;
    consumed.current = true; active.current = controller;
    setState({ binding, phase: 'submitting', submitted, error: null });
    try {
      const accepted = await boundedRunRequest(() => miningRunApi.create(submitted, controller.signal), controller, RUN_POST_TIMEOUT);
      if (latest.current !== ticket || active.current !== controller) return;
      setState({ binding, phase: 'accepted', submitted, error: null });
      onAccepted?.(accepted);
    } catch (failure) {
      if (latest.current !== ticket || active.current !== controller) return;
      const error = runRequestError(failure);
      // In particular 409/503 may follow commit. Neither transport abort nor timeout cancels server work.
      const rejected = error.kind === 'http' && [400, 401, 403, 404, 422].includes(error.status ?? 0);
      setState({ binding, phase: rejected ? 'rejected' : 'uncertain', submitted, error });
    } finally { if (active.current === controller) active.current = null; }
  }
  function reset() {
    if (active.current) return;
    consumed.current = false; setState(blank());
  }
  return { ...visible, allowed, create, reset };
}
