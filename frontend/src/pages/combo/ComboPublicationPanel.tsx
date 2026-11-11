import React, { useEffect, useRef, useState } from 'react';
import { Alert, Button, Stack, Text } from '@mantine/core';
import { ComboClient, ComboClientError } from './ComboClient';

export default function ComboPublicationPanel({ batchId, client, onPublished, batchStatus }: {
  batchId: string; client: ComboClient; onPublished: () => void; batchStatus?: string;
}) {
  const [busy, setBusy] = useState(false), [message, setMessage] = useState(''), [error, setError] = useState(false);
  const active = useRef<AbortController | null>(null);
  useEffect(() => () => { active.current?.abort(); active.current = null; }, []);
  async function publish() {
    if (active.current || !client.publish) return;
    const controller = new AbortController(); active.current = controller;
    setBusy(true); setMessage('');
    try {
      const result = await client.publish(batchId, { signal: controller.signal });
      if (controller.signal.aborted) return;
      setError(false); setMessage(`Đã kích hoạt batch. ${result.publishedCount} combo đã được công bố.`);
      onPublished();
    } catch (failure) {
      if (controller.signal.aborted) return;
      const known = failure instanceof ComboClientError;
      const reason = known && failure.code === 'INVALID_RESOURCE_STATE'
        ? 'Backend từ chối công bố: batch phải là DRAFT và có combo APPROVED vượt qua kiểm tra nguồn, dữ liệu hiện tại.'
        : known && failure.status === 403 ? 'Tài khoản hiện tại không có quyền công bố batch.'
          : known && failure.status === 401 ? 'Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại.'
            : 'Chưa xác nhận công bố. Hãy tải lại trạng thái batch trước khi thử lại.';
      setError(true); setMessage(reason + (known ? ` Mã lỗi: ${failure.code}.` + (failure.requestId ? ` Mã tra cứu: ${failure.requestId}.` : '') : ''));
    } finally {
      if (active.current === controller) { active.current = null; setBusy(false); }
    }
  }
  if (!client.publish) return null;
  return <Stack spacing="xs">
    {batchStatus === 'DRAFT' && !message && <Alert color="orange" title="Batch chưa được công bố">
      Duyệt nội bộ chưa đưa combo lên client. Hãy bấm công bố bên dưới để kích hoạt batch; khởi động lại ứng dụng không thay đổi trạng thái này.
    </Alert>}
    <Text size="sm">Duyệt xong trước khi công bố. Các loại mua cùng đơn, mua theo thứ tự và kết hợp đã APPROVED đều được kiểm tra nguồn trước khi công bố. Batch đang hoạt động trước đó sẽ được lưu trữ; batch đã kích hoạt không tiếp tục duyệt.</Text>
    <Button color="teal" loading={busy} disabled={busy} onClick={() => void publish()}>Công bố batch cho khách hàng</Button>
    {message && <Alert role={error ? 'alert' : 'status'} color={error ? 'yellow' : 'teal'}>{message}</Alert>}
  </Stack>;
}
