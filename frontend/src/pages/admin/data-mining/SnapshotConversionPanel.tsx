import React, { useEffect } from 'react';
import { Alert, Button, Group, Loader, Paper, Stack, Text, Title } from '@mantine/core';
import { ConversionStatistics, SnapshotDetail, ValidationReport } from 'api/admin/data-mining';
import { previewAllowed } from 'api/admin/data-mining/conversion';
import { useSnapshotConversion } from './useSnapshotConversion';

const labels: Record<keyof ConversionStatistics, string> = {
  sequenceCount: 'Số sequence', singleItemsetSequenceCount: 'Sequence có một itemset', multiItemsetSequenceCount: 'Sequence có nhiều itemset',
  itemsetCount: 'Số itemset', distinctItemCount: 'Số item phân biệt', totalItemOccurrences: 'Tổng số lần xuất hiện item',
  minSequenceLength: 'Độ dài sequence nhỏ nhất', maxSequenceLength: 'Độ dài sequence lớn nhất', averageSequenceLength: 'Độ dài sequence trung bình',
};
export default function SnapshotConversionPanel({ detail, validationReport, onComplete, onActivityChange }: {
  detail: SnapshotDetail; validationReport: ValidationReport | null; onComplete: (signal: AbortSignal) => Promise<void>;
  onActivityChange?: (activity: { busy: boolean; error: boolean }) => void;
}) {
  const eligible = detail.validationStatus === 'VALID' && detail.rawAvailability === 'AVAILABLE'
    && validationReport?.snapshotId === detail.snapshotId && validationReport.rawSha256 === detail.rawSha256
    && validationReport.rawSizeBytes === detail.rawSizeBytes && validationReport.validationStatus === 'VALID'
    && validationReport.cleaningOutcome === 'READY' && validationReport.countsComplete;
  const state = useSnapshotConversion(detail, !!eligible, onComplete), report = state.report, busy = !!state.busy;
  const statistics = report?.statistics, sequence = report?.sequence, mapping = report?.mapping;
  useEffect(() => { onActivityChange?.({ busy, error: !!state.error }); }, [busy, state.error, onActivityChange]);
  return <Paper component="section" aria-label="Chuyển đổi dữ liệu" withBorder radius="md" p="lg"><Stack>
    <Group position="apart"><Title order={3}>Chuyển đổi dữ liệu</Title>
      <Text role="status" size="sm">Trạng thái conversion: {report?.conversionStatus ?? 'Chưa xác nhận'}</Text></Group>
    <Text size="sm">Chuyển dữ liệu sau cleaning sang SPMF và mapping sản phẩm. Máy chủ xác minh dữ liệu trước khi công bố kết quả.</Text>
    {state.error && <Alert role="alert" color="red" title="Chưa thể xác nhận conversion">
      <Text>{state.error.status === 401 ? 'Phiên đăng nhập không còn hợp lệ.' : state.error.status === 403 ? 'Tài khoản không có quyền chuyển đổi.'
        : 'Chưa xác nhận được dữ liệu hoặc kết nối bị gián đoạn. Hãy đọc lại trạng thái conversion.'}</Text>
      {state.error.requestId && <Text>Mã yêu cầu: {state.error.requestId}</Text>}
    </Alert>}
    {report?.conversionStatus === 'NOT_STARTED' && !eligible && <Text size="sm">Hãy hoàn tất bước kiểm tra dữ liệu. Chỉ chuyển đổi khi báo cáo xác nhận dữ liệu hợp lệ và đã đọc đầy đủ snapshot này.</Text>}
    {report?.conversionStatus === 'FAILED' && <Alert color="red">Chuyển đổi thất bại: {report.failureCode}. Không hỗ trợ gửi lại conversion.</Alert>}
    {(report?.conversionStatus === 'RUNNING' || state.busy === 'post') && <Group spacing="xs">
      <Loader size="sm" aria-label="Đang chuyển đổi dữ liệu"/><Text>Đang xử lý conversion trên máy chủ…</Text>
    </Group>}
    {state.pollingStopped && report?.conversionStatus === 'RUNNING' && <Text>Đã ngừng tự đọc trạng thái. Hãy đọc lại thủ công để tiếp tục theo dõi.</Text>}
    {state.postConsumed && report?.conversionStatus === 'NOT_STARTED' && <Text>Yêu cầu đã được gửi trong phiên xem này. Hãy đối soát trạng thái trước khi mở lại trang.</Text>}
    <Group>
      {report?.conversionStatus === 'NOT_STARTED' && <Button onClick={() => void state.start()} loading={state.busy === 'post'}
        disabled={busy || !eligible || state.postConsumed}>Chuyển đổi dữ liệu</Button>}
      <Button variant="light" loading={state.busy === 'read'} disabled={busy} onClick={() => void state.read()}>Đọc lại trạng thái conversion</Button>
    </Group>
    {(busy || report?.conversionStatus === 'RUNNING') && <Text size="sm" color="dimmed" data-secondary>Bạn có thể đổi tab trong khi chờ. Rời trang hoặc đăng xuất sẽ ngừng chờ trên trình duyệt; máy chủ có thể vẫn đang xử lý.</Text>}
    {report?.conversionStatus === 'SUCCEEDED' && statistics && sequence && mapping && <>
      <Title order={4}>Thống kê sequence</Title>
      <Text size="sm">Độ dài sequence là tổng số item trong sequence; không phải số đơn hoặc tổng quantity.</Text>
      <div className="data-metrics data-metrics-conversion">
        {(Object.keys(labels) as Array<keyof ConversionStatistics>).map(key => <div className="data-metric" role="group" aria-label={labels[key]} key={key}>
          <Text color="dimmed" data-secondary size="sm">{labels[key]}</Text><Text>{statistics[key]}</Text>
        </div>)}
      </div>
      <Text size="sm">Hoàn tất (UTC): {report.completedAt}</Text>
      <Title order={4}>Tải kết quả</Title>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 260px), 1fr))', gap: 16 }}>
        {(['sequence', 'mapping'] as const).map(kind => <Paper withBorder p="md" radius="md" key={kind} role="group" aria-label={kind === 'sequence' ? 'Artifact SPMF' : 'Artifact mapping'}>
          <Text>{kind === 'sequence' ? 'SPMF' : 'Mapping JSON'}: {(kind === 'sequence' ? sequence : mapping).sizeBytes} byte</Text>
          <Text size="sm" style={{ overflowWrap: 'anywhere' }}>SHA-256 do máy chủ cung cấp: {(kind === 'sequence' ? sequence : mapping).sha256}</Text>
          <Button variant="light" disabled={busy} loading={state.busy === kind} onClick={() => void state.download(kind)}>
            {kind === 'sequence' ? 'Tải SPMF' : 'Tải mapping JSON'}</Button>
        </Paper>)}</div>
      <Button variant="light" disabled={busy || !previewAllowed(sequence.sizeBytes)} loading={state.busy === 'preview'}
        onClick={() => void state.previewArtifact()}>Xem trước SPMF</Button>
      <Text size="sm">Xem trước tối đa 20 sequence / 4000 ký tự, chỉ với tệp không quá 1 MiB. Tệp lớn vẫn có thể tải xuống.</Text>
      {state.preview && <div><Text>{state.preview.truncated ? 'Bản xem trước một phần — không phải toàn bộ artifact.' : 'Bản xem trước toàn bộ artifact nhỏ.'}</Text>
        {!state.preview.text && <Text>Không có sequence đầy đủ vừa giới hạn xem trước. Hãy tải SPMF để xem nội dung.</Text>}
        <pre aria-label="Nội dung xem trước SPMF" style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{state.preview.text}</pre></div>}
    </>}
    {state.sent && <Text role="status">Đã gửi {state.sent} để trình duyệt tải xuống.</Text>}
  </Stack></Paper>;
}
