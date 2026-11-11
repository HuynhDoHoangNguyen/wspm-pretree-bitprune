import { useEffect, useRef, useState } from 'react';
import { WdmApiError } from 'api/admin/data-mining';

export function useDatasetRequest() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<WdmApiError | null>(null);
  const active = useRef<AbortController | null>(null);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; active.current?.abort(); };
  }, []);

  async function run<T>(work: (signal: AbortSignal) => Promise<T>, success: (result: T) => void) {
    if (active.current) return;
    const controller = new AbortController();
    active.current = controller;
    setBusy(true); setError(null);
    try {
      const result = await work(controller.signal);
      if (mounted.current && !controller.signal.aborted) success(result);
    } catch (failure) {
      if (mounted.current && !controller.signal.aborted) {
        setError(failure instanceof WdmApiError ? failure
          : new WdmApiError('response', null, 'OPERATION_FAILED', 'Operation could not be confirmed.'));
      }
    } finally {
      if (mounted.current && active.current === controller) { active.current = null; setBusy(false); }
    }
  }
  return { busy, error, run, clearError: () => setError(null) };
}
