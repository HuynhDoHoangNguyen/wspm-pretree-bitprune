import { useEffect, useState } from 'react';
import { useSharedAuth } from 'auth/session';
import { WdmApiError } from 'api/admin/data-mining';

/** Binding includes identity/token: protected data disappears immediately on session change. */
export function useWorkspaceRead<T>(key: string, load: (signal: AbortSignal) => Promise<T>, refreshMs = 0) {
  const auth = useSharedAuth();
  const allowed = auth.status === 'authenticated' && !!auth.accessToken && !auth.forbidden && !!auth.user?.roles.includes('ADMIN');
  const binding = JSON.stringify([key, auth.status, auth.user?.userId, auth.user?.roles, auth.accessToken, auth.forbidden]);
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<{ binding: string; value: T | null; loading: boolean; error: WdmApiError | null }>({ binding: '', value: null, loading: false, error: null });
  useEffect(() => {
    if (!allowed || !key) return;
    const controller = new AbortController(); let timer: ReturnType<typeof setTimeout> | undefined; let busy = false;
    async function read() {
      if (busy || controller.signal.aborted || document.hidden) return;
      busy = true;
      setState(previous => ({ binding, value: previous.binding === binding ? previous.value : null, loading: true, error: null }));
      try {
        const value = await load(controller.signal);
        if (!controller.signal.aborted) setState({ binding, value, loading: false, error: null });
      } catch (failure) {
        const error = failure instanceof WdmApiError ? failure : new WdmApiError('network', null, 'NETWORK_ERROR', 'Read unavailable.');
        if (!controller.signal.aborted) setState({ binding, value: null, loading: false, error });
      } finally { busy = false; if (refreshMs && !controller.signal.aborted) timer = setTimeout(() => void read(), refreshMs); }
    }
    const visibility = () => { if (!document.hidden) { clearTimeout(timer); void read(); } };
    void read(); document.addEventListener('visibilitychange', visibility);
    return () => { controller.abort(); clearTimeout(timer); document.removeEventListener('visibilitychange', visibility); };
    // load is an inline operation; key identifies its complete request inputs.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [allowed, binding, key, revision, refreshMs]);
  return { ...(allowed && state.binding === binding ? state : { value: null, loading: allowed && !!key, error: null }), refresh: () => setRevision(n => n + 1) };
}
