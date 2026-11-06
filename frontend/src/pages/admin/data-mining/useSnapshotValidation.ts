import { useEffect, useRef, useState } from 'react';
import { useSharedAuth } from 'auth/session';
import { SnapshotDetail, validationApi, ValidationReport, WdmApiError } from 'api/admin/data-mining';

type Action = 'read' | 'post';
interface State { binding: string; report: ValidationReport | null; busy: Action | null; error: WdmApiError | null; recheckRequired: boolean }
export function useSnapshotValidation(detail: SnapshotDetail, onComplete: (signal: AbortSignal) => Promise<void>) {
  const { user, status, forbidden } = useSharedAuth();
  const binding = JSON.stringify([detail.snapshotId, detail.rawSha256, detail.rawSizeBytes, user?.userId, status, forbidden]);
  const latest = useRef(binding); latest.current = binding;
  const active = useRef<AbortController | null>(null);
  const completed = useRef(onComplete); completed.current = onComplete;
  const blank = (): State => ({ binding, report: null, busy: null, error: null, recheckRequired: false });
  const [state, setState] = useState<State>(blank);

  async function run(action: Action, page = 0) {
    if (active.current || status !== 'authenticated' || forbidden) return;
    const controller = new AbortController(), ticket = binding;
    active.current = controller;
    const current = () => !controller.signal.aborted && latest.current === ticket;
    setState(previous => ({ ...(previous.binding === ticket ? previous : blank()), busy: action, error: null }));
    try {
      const report = action === 'post' ? await validationApi.start(detail.snapshotId, controller.signal)
        : await validationApi.read(detail.snapshotId, page, 20, controller.signal);
      if (!current()) return;
      if (report.rawSha256 !== detail.rawSha256 || report.rawSizeBytes !== detail.rawSizeBytes) {
        throw new WdmApiError('response', 200, 'INVALID_VALIDATION_BINDING', 'Report does not match snapshot metadata.');
      }
      setState({ binding: ticket, report, busy: action, error: null, recheckRequired: false });
      if (action === 'post' || detail.validationStatus !== report.validationStatus) await completed.current(controller.signal);
    } catch (failure) {
      if (!current()) return;
      const error = failure instanceof WdmApiError ? failure
        : new WdmApiError('response', null, 'INVALID_VALIDATION_RESPONSE', 'Report could not be confirmed.');
      if (error.kind === 'aborted') {
        setState({ ...blank(), recheckRequired: action === 'post' });
      } else setState(previous => ({ ...previous, error,
        recheckRequired: action === 'post' ? (['network', 'response'].includes(error.kind) || [409, 503].includes(error.status ?? 0))
          : previous.recheckRequired && !(error.kind === 'http' && error.status === 409) }));
    } finally {
      if (current()) setState(previous => ({ ...previous, busy: null }));
      if (active.current === controller) active.current = null;
    }
  }

  useEffect(() => {
    setState(blank());
    if (['VALID', 'INVALID'].includes(detail.validationStatus || '')) void run('read');
    return () => { active.current?.abort(); active.current = null; };
    // Binding includes identity and immutable snapshot metadata; metadata refresh must not replay the POST.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [binding]);

  return { ...(state.binding === binding ? state : blank()), start: () => run('post'), read: (page = 0) => run('read', page) };
}
