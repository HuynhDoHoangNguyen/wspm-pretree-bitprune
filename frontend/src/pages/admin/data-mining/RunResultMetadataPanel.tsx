import React, { useEffect, useRef } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Alert, Badge, Button, Group, Paper, Stack, Text, Title } from '@mantine/core';
import { runResultApi } from 'api/admin/data-mining';
import { isRunId } from 'api/admin/data-mining/mining-run-response';
import { useWorkspaceRead } from './useWorkspaceRead';
import { ReadError, StateBadge } from './WorkspacePanels';
import CopyValue from './CopyValue';
import RunResultExportButton from './RunResultExportButton';

/** Publication is independently refreshed, never inferred from the execution status report. */
export default function RunResultMetadataPanel({ runId }: { runId: string }) {
  const location = useLocation();
  const read = useWorkspaceRead(isRunId(runId) ? 'result-metadata:' + runId : '', signal => runResultApi.metadata(runId, signal));
  const info = read.value;
  const refresh = useRef(read.refresh);
  refresh.current = read.refresh;
  const awaitingPublication = !!info && !info.publishedReadable && info.publicationStatus === 'NOT_PUBLISHED';
  // Execution may finish before the consumer commits. Refresh only while awaiting handoff;
  // the shared read hook owns abort/session/visibility handling and errors stop polling.
  useEffect(() => {
    if (!awaitingPublication || read.loading || read.error) return;
    const timer = setTimeout(() => refresh.current(), 2000);
    return () => clearTimeout(timer);
  }, [awaitingPublication, read.loading, read.error, info]);
  return <Paper withBorder radius="md" p="lg" component="section" aria-label="Lưu trữ và công bố kết quả"><Stack spacing="sm">
    <Group position="apart"><Title order={3}>Lưu trữ và công bố kết quả</Title>
      <Button size="xs" variant="light" disabled={read.loading} onClick={read.refresh}>Làm mới kết quả lưu</Button></Group>
    {read.loading && <Text role="status">Đang đọc metadata kết quả…</Text>}
    {read.error && (read.error.status === 409 ? <Alert color="yellow" role="alert" title="Chưa xác minh được metadata kết quả">
      <Text>Metadata thiếu một phần, không nhất quán hoặc có phiên bản chưa được hỗ trợ. Không thể kết luận kết quả đã công bố.</Text>
      {read.error.requestId && <Text size="xs">Mã yêu cầu: {read.error.requestId}</Text>}<Button variant="subtle" onClick={read.refresh}>Thử lại</Button>
    </Alert> : <ReadError retry={read.refresh} error={read.error}/>) }
    {info && <>
      <Group><Text size="sm">Trạng thái thực thi lúc đọc kết quả:</Text><StateBadge value={info.executionStatus}/>
        <Badge color={info.publishedReadable ? 'teal' : 'orange'}>{info.publishedReadable ? 'Đã công bố và kiểm chứng' : info.publicationStatus === 'NOT_PUBLISHED' ? 'Chưa công bố' : info.publicationStatus === 'COMMITTED' ? 'Có marker công bố · chưa xác minh' : 'Lịch sử chưa xác minh'}</Badge></Group>
      <Alert color={info.publishedReadable ? 'teal' : 'yellow'}>{info.publishedReadable ? 'Kết quả đã lưu đầy đủ và khớp manifest công bố.' : info.publicationStatus === 'NOT_PUBLISHED'
        ? 'Dữ liệu đang ở staging. Run hoàn tất không có nghĩa kết quả đã được công bố đầy đủ. Không dùng số hàng đã lưu để kết luận engine không tạo patterns.'
        : info.publicationStatus === 'COMMITTED'
          ? 'Database có marker công bố, nhưng phiên bản hiện tại chưa hỗ trợ xác minh manifest. Chưa thể xác nhận kết quả đầy đủ.'
          : 'Run lịch sử thiếu provenance/publication đã xác minh. Không suy diễn kết quả đã được lưu đầy đủ.'}</Alert>
      <Group spacing="xl"><Text>Pattern đã lưu: <strong>{info.storedPatternCount}</strong></Text>
        <Text>Temporal rows: <strong>{info.storedPatternItemCount}</strong></Text><Text>Item statistics: <strong>{info.storedRunItemCount}</strong></Text></Group>
      <Text size="sm">{info.publishedReadable ? (info.eligibleForCombo ? 'Đã kiểm chứng đầy đủ · Đủ điều kiện bàn giao đầu vào Combo.' : 'Đã kiểm chứng đầy đủ · Không có pattern để sinh Combo.') : 'Tính đầy đủ: chưa xác minh · Chưa đủ điều kiện bàn giao Combo.'}</Text>
      {info.publishedReadable && <><CopyValue label="SHA-256 kết quả" value={info.resultSha256 || ''}/><RunResultExportButton runId={runId} view="published"/>
        <Button component={Link} to={'/admin/patterns?runId=' + runId + '&page=1&view=published'} state={{ runReturnTo: location.state }}>Xem pattern đã công bố</Button></>}
      {!read.loading && info.stagingReadable && <Button component={Link} variant="light"
        to={'/admin/patterns?runId=' + runId + '&page=1'} state={{ runReturnTo: location.state }}>Xem pattern staging</Button>}
      {info.provenance && <details><summary>Nguồn dữ liệu đã ghi nhận cho run</summary><Stack mt="sm" spacing="xs">
        <CopyValue label="Snapshot lịch sử" value={info.provenance.snapshotId}/>
        <CopyValue label="Conversion attempt" value={info.provenance.attemptId}/>
        <Text size="sm">{info.provenance.version} · {info.provenance.conversionFormatVersion} · {info.provenance.sequenceCount} sequences</Text>
        <CopyValue label="SHA-256 SPMF lịch sử" value={info.provenance.sequenceSha256}/>
        <CopyValue label="SHA-256 mapping lịch sử" value={info.provenance.mappingSha256}/>
      </Stack></details>}
      <Text size="xs" color="dimmed">Metadata kết quả được đọc riêng với trạng thái/metrics, có thể khác thời điểm cập nhật. Đường dẫn tệp hiện tại của dataset không chứng minh tệp lịch sử của run.</Text>
    </>}
  </Stack></Paper>;
}
