import { useEffect, useRef, useState } from 'react';
import { useSharedAuth } from 'auth/session';
import { conversionApi, ConversionReport, SnapshotDetail, WdmApiError } from 'api/admin/data-mining';
import { downloadDatasetFile } from './downloadDatasetFile';

type Action = 'read' | 'post' | 'preview' | 'sequence' | 'mapping';
interface State {
  binding: string; report: ConversionReport | null; busy: Action | null; error: WdmApiError | null;
  preview: { text: string; truncated: boolean } | null; sent: string | null; pollingStopped: boolean; postConsumed: boolean;
}
export function useSnapshotConversion(detail: SnapshotDetail, eligible: boolean, onComplete: (signal: AbortSignal) => Promise<void>) {
  const { user, status, forbidden } = useSharedAuth();
  const binding = JSON.stringify([detail.snapshotId, detail.rawSha256, detail.rawSizeBytes, user?.userId, status, forbidden]);
  const latest = useRef(binding); latest.current = binding;
  const active = useRef<AbortController | null>(null), consumed = useRef(false), deadline = useRef<number | null>(null);
  const completed = useRef(onComplete); completed.current = onComplete;
  const blank = (): State => ({ binding, report: null, busy: null, error: null, preview: null, sent: null, pollingStopped: false, postConsumed: false });
  const [state, setState] = useState<State>(blank);
  const visible = state.binding === binding ? state : blank();

  async function run(action: Action, manual = false) {
    if (active.current || status !== 'authenticated' || forbidden) return;
    if (action === 'post' && (!eligible || consumed.current || visible.report?.conversionStatus !== 'NOT_STARTED')) return;
    if (!['read', 'post'].includes(action) && visible.report?.conversionStatus !== 'SUCCEEDED') return;
    const artifact = action === 'mapping' ? visible.report?.mapping : visible.report?.sequence;
    if (!['read', 'post'].includes(action) && !artifact) return;
    const controller = new AbortController(), ticket = binding;
    active.current = controller;
    const current = () => !controller.signal.aborted && latest.current === ticket;
    if (action === 'post') consumed.current = true;
    if (manual) deadline.current = null;
    setState(previous => ({ ...previous, busy: action, error: null, sent: null, postConsumed: consumed.current,
      pollingStopped: manual ? false : previous.pollingStopped }));
    async function accept(report: ConversionReport) {
      if (!current()) return;
      if (report.conversionStatus === 'RUNNING' && deadline.current === null) deadline.current = Date.now() + 60000;
      setState(previous => ({ ...previous, report, error: null, preview: null, pollingStopped: false }));
      if (report.conversionStatus === 'SUCCEEDED' && (action === 'post' || detail.conversionStatus !== 'SUCCEEDED')) await completed.current(controller.signal);
    }
    try {
      if (action === 'read') await accept(await conversionApi.read(detail.snapshotId, controller.signal));
      else if (action === 'post') {
        await conversionApi.start(detail.snapshotId, controller.signal);
        if (current()) await accept(await conversionApi.read(detail.snapshotId, controller.signal));
      } else if (action === 'preview') {
        if (!artifact) return;
        const preview = await conversionApi.preview(detail.snapshotId, artifact, controller.signal);
        if (current()) setState(previous => ({ ...previous, preview }));
      } else {
        if (!artifact) return;
        const result = await conversionApi.download(detail.snapshotId, action, artifact, controller.signal);
        if (current()) { downloadDatasetFile(result); setState(previous => ({ ...previous, sent: result.filename })); }
      }
    } catch (failure) {
      if (!current()) return;
      const error = failure instanceof WdmApiError ? failure : new WdmApiError('response', null, 'INVALID_CONVERSION_RESPONSE', 'Conversion could not be confirmed.');
      setState(previous => ({ ...previous, error, report: ['read', 'post'].includes(action) ? null : previous.report,
        preview: null, pollingStopped: true }));
      // A lost POST response never grants permission to replay the POST.
      if (action === 'post' && error.kind !== 'aborted' && ![400, 401, 403, 404].includes(error.status ?? 0)) {
        try { await accept(await conversionApi.read(detail.snapshotId, controller.signal)); }
        catch { /* Keep the original safe error and allow a manual GET. */ }
      }
    } finally {
      if (current()) setState(previous => ({ ...previous, busy: null }));
      if (active.current === controller) active.current = null;
    }
  }
  useEffect(() => {
    consumed.current = false; deadline.current = null; setState(blank());
    void run('read');
    return () => { active.current?.abort(); active.current = null; };
    // Immutable identity and Auth bind all requests; metadata refresh must not replay a POST.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [binding]);
  useEffect(() => {
    if (visible.report?.conversionStatus !== 'RUNNING' || visible.busy || visible.pollingStopped) return;
    const timer = window.setTimeout(() => {
      if (Date.now() >= (deadline.current ?? 0)) setState(previous => ({ ...previous, pollingStopped: true }));
      else void run('read');
    }, 2000);
    return () => window.clearTimeout(timer);
    // Each GET finishes before the next timer is armed.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [binding, visible.report, visible.busy, visible.pollingStopped]);
  return { ...visible, start: () => run('post'), read: () => run('read', true), previewArtifact: () => run('preview'),
    download: (kind: 'sequence' | 'mapping') => run(kind) };
}
