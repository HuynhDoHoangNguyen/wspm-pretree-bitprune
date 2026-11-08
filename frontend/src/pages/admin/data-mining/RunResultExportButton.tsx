import React, { useEffect, useRef, useState } from 'react';
import { Alert, Button, Stack, Text } from '@mantine/core';
import { useSharedAuth } from 'auth/session';
import { runResultApi, WdmApiError } from 'api/admin/data-mining';
import { ResultView } from 'api/admin/data-mining/run-results';
import { downloadDatasetFile } from './downloadDatasetFile';

export default function RunResultExportButton({ runId, view }: { runId: string; view: ResultView }) {
  const auth = useSharedAuth();
  const allowed = auth.status === 'authenticated' && !!auth.accessToken && !auth.forbidden && !!auth.user?.roles.includes('ADMIN');
  const binding = JSON.stringify([runId, view, auth.status, auth.user?.userId, auth.user?.roles, auth.accessToken, auth.forbidden]);
  const latest = useRef(binding); latest.current = binding;
  const active = useRef<AbortController | null>(null);
  const [state, setState] = useState<{ binding: string; busy: boolean; sent?: boolean; error?: WdmApiError }>({ binding: '', busy: false });
  useEffect(() => { latest.current = binding; return () => { latest.current = ''; active.current?.abort(); active.current = null; }; }, [binding]);
  const current = state.binding === binding ? state : { binding, busy: false };
  async function download() {
    if (!allowed || active.current) return;
    const controller = new AbortController(); active.current = controller;
    setState({ binding, busy: true });
    try {
      const file = await runResultApi.export(runId, view, controller.signal);
      if (!controller.signal.aborted && latest.current === binding) {
        downloadDatasetFile(file); setState({ binding, busy: false, sent: true });
      }
    } catch (failure) {
      if (!controller.signal.aborted && latest.current === binding) setState({ binding, busy: false,
        error: failure instanceof WdmApiError ? failure : new WdmApiError('response', null, 'EXPORT_FAILED', 'Không thể xác nhận tệp export.') });
    } finally { if (active.current === controller) active.current = null; }
  }
  if (!allowed) return null;
  return <Stack spacing="xs">
    <Button loading={current.busy} disabled={current.busy} onClick={() => void download()}>
      {view === 'published' ? 'Tải JSON đã công bố' : 'Tải JSON staging để kiểm tra'}
    </Button>
    {current.error && <Alert color="red" role="alert">{current.error.status === 413 ? 'Tệp kết quả vượt giới hạn tải được hỗ trợ.'
      : current.error.status === 409 ? 'Kết quả chưa cho phép xuất hoặc không vượt qua kiểm chứng. Hãy làm mới thông tin run.'
        : current.error.status === 401 || current.error.status === 403 ? 'Phiên đăng nhập không còn quyền tải kết quả.'
          : 'Chưa thể xác nhận tệp kết quả. Bạn có thể thử tải lại.'}
    {current.error.requestId && <Text size="xs">Mã yêu cầu: {current.error.requestId}</Text>}</Alert>}
    {current.sent && <Text role="status">Đã gửi tệp JSON để trình duyệt tải xuống.</Text>}
  </Stack>;
}
