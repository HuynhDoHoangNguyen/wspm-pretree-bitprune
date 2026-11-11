import React, { useEffect, useState } from 'react';
import { Alert, Button, Group, Paper, Stack, Text, TextInput, Title } from '@mantine/core';
import { Link, useInRouterContext, useSearchParams } from 'react-router-dom';
import { comboInputApi } from 'api/admin/data-mining/combo-input';
import { isRunId } from 'api/admin/data-mining/mining-run-response';
import { useWorkspaceRead } from '../admin/data-mining/useWorkspaceRead';
import { ReadError } from '../admin/data-mining/WorkspacePanels';
import NativeGenerationPanel from './NativeGenerationPanel';

const messages: Record<string, string> = {
  BUSINESS_POLICY_UNCONFIRMED: 'Cần xác nhận chính sách chọn Combo, top-K, neo và điều kiện sản phẩm.',
  PUBLISHED_RESULT_WRITER_PROTOCOL_REQUIRED: 'Cần thống nhất giao thức lưu Combo với manifest đã công bố của nhóm mining.',
  REVIEW_POLICY_UNCONFIRMED: 'Cần cấu hình chính sách giá và thẩm quyền duyệt từ phía máy chủ.',
};
export default function PublishedComboInputPanel() {
  const routed = useInRouterContext();
  return routed ? <RoutedInputPanel/> : null;
}
function RoutedInputPanel() {
  const [params, setParams] = useSearchParams();
  const ids = params.getAll('runId');
  const id = ids.length === 1 ? ids[0] : '';
  const valid = isRunId(id);
  const [input, setInput] = useState(id);
  const [invalid, setInvalid] = useState(false);
  useEffect(() => { setInput(id); setInvalid(false); }, [id]);
  const read = useWorkspaceRead(valid ? 'combo-input:' + id : '', signal => comboInputApi.read(id, signal));
  return <Paper withBorder p="lg" component="section" aria-label="Đầu vào Combo từ mining"><Stack>
    <Title order={3}>Kiểm tra và sinh Combo từ mining</Title>
    <Text size="sm">Chọn lần khai phá đã công bố để kiểm tra đầu vào, preview và sinh Combo DRAFT. Batch ID chỉ có sau khi lưu Combo thành công.</Text>
    <form onSubmit={event => {
      event.preventDefault();
      const selected = input.trim();
      if (!isRunId(selected)) { setInvalid(true); return; }
      setInvalid(false);
      const next = new URLSearchParams(params); next.set('runId', selected); setParams(next);
      if (selected === id) read.refresh();
    }}><Stack spacing="sm">
        <TextInput label="Run ID nguồn để sinh Combo" placeholder="Nhập mã lần chạy đã công bố" value={input}
          onChange={event => setInput(event.currentTarget.value)} error={invalid ? 'Nhập UUID Run ID hợp lệ.' : undefined}/>
        <Group><Button type="submit" loading={read.loading}>Dùng run này</Button>
          <Button component={Link} to="/admin/mining-runs?tab=history" variant="light">Chọn run từ lịch sử</Button></Group>
      </Stack></form>
    {!!ids.length && !valid && <Alert color="red">Run ID không hợp lệ.</Alert>}
    {read.loading && <Text role="status">Đang xác minh toàn bộ kết quả mining…</Text>}
    {read.error && <ReadError error={read.error} retry={read.refresh}/>}
    {!read.loading && !read.error && read.value && <>
      <Text>Run: {read.value.runId}</Text>
      <Text>Đã xác minh {read.value.patternCount} Pattern và {read.value.dictionaryCount} sản phẩm trong dictionary lịch sử.</Text>
      {read.value.generationStatus === 'READY_FOR_PREVIEW' ? <NativeGenerationPanel key={id} runId={id}/> : <Alert color="yellow" title="Đã nhận đầu vào; chưa cho phép sinh Combo">
        <Stack spacing="xs">{read.value.blockers.map(b => <Text key={b}>{messages[b] || b}</Text>)}</Stack>
      </Alert>}
      <Group><Button component={Link} to={'/admin/patterns?runId=' + id + '&view=published'} variant="light">Xem Pattern nguồn</Button></Group>
    </>}
  </Stack></Paper>;
}
