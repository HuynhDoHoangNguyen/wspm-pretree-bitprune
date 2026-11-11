import { useEffect, useRef, useState } from 'react';
import { useSharedAuth } from 'auth/session';
import { miningApi, MiningRequest, MiningPreview, MiningValidation, WdmApiError } from 'api/admin/data-mining';
import { sameMiningInput } from 'api/admin/data-mining/mining-response';
import { hasDataMiningAdminAccess } from './access';

interface Result { binding: string; preview: MiningPreview | null; validation: MiningValidation | null; busy: boolean; error: WdmApiError | null }
export function useMiningConfiguration(revision: string) {
  const auth = useSharedAuth();
  const binding = JSON.stringify([revision, auth.user?.userId, auth.user?.roles, auth.status, auth.forbidden, auth.accessToken]);
  const latest = useRef(binding); latest.current = binding;
  const active = useRef<AbortController | null>(null);
  const allowed = auth.status === 'authenticated' && !auth.forbidden && hasDataMiningAdminAccess(auth.user);
  const blank = (): Result => ({ binding, preview: null, validation: null, busy: false, error: null });
  const [state, setState] = useState<Result>(blank);
  const visible = state.binding === binding ? state : blank();
  const snapshot = useRef(visible); snapshot.current = visible;
  function invalidate() {
    latest.current = ''; active.current?.abort(); active.current = null;
    setState(blank());
  }
  useEffect(() => {
    latest.current = binding;
    active.current?.abort(); active.current = null;
    setState({ binding, preview: null, validation: null, busy: false, error: null });
    return () => { active.current?.abort(); active.current = null; latest.current = ''; };
  }, [binding]);

  async function run(action: 'preview' | 'validate', request: MiningRequest) {
    if (!allowed || active.current || latest.current !== binding) return;
    const controller = new AbortController(), ticket = binding;
    const previous = snapshot.current.preview;
    active.current = controller;
    const current = () => !controller.signal.aborted && latest.current === ticket;
    setState({ binding, preview: action === 'validate' ? previous : null, validation: null, busy: true, error: null });
    try {
      if (action === 'preview') {
        const preview = await miningApi.preview(request, controller.signal);
        if (current()) setState({ binding, preview, validation: null, busy: false, error: null });
      } else {
        const validation = await miningApi.validate(request, controller.signal);
        if (!current()) return;
        if (previous && !sameMiningInput(previous, validation)) {
          throw new WdmApiError('response', 200, 'MINING_BINDING_CHANGED', 'Mining input changed.');
        }
        setState({ binding, preview: previous, validation, busy: false, error: null });
      }
    } catch (failure) {
      if (current()) setState({ binding, preview: null, validation: null, busy: false,
        error: failure instanceof WdmApiError ? failure : new WdmApiError('response', null, 'INVALID_MINING_RESPONSE', 'Mining result could not be confirmed.') });
    } finally { if (active.current === controller) active.current = null; }
  }
  return { ...visible, allowed, invalidate, run };
}
