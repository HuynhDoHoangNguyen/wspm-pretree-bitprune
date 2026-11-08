import React from 'react';
import { Alert, Button, Group, Paper, Stack, Text, Title } from '@mantine/core';
import { Link, useLocation, useParams, useSearchParams } from 'react-router-dom';
import { runResultApi } from 'api/admin/data-mining';
import { isPatternId } from 'api/admin/data-mining/run-results';
import { isRunId } from 'api/admin/data-mining/mining-run-response';
import useTitle from 'hooks/use-title';
import { useWorkspaceRead } from './useWorkspaceRead';
import { ReadError } from './WorkspacePanels';
import CopyValue from './CopyValue';
import PatternStructure from './PatternStructure';

export default function PatternDetailPage() {
  useTitle('Chi tiết pattern');
  const { runId = '', patternId = '' } = useParams();
  const [params] = useSearchParams();
  const location = useLocation();
  const page = params.get('page') || '1';
  const view = params.get('view') === 'published' ? 'published' : 'staging';
  const returnPage = /^[1-9][0-9]{0,5}$/.test(page) && Number(page) <= 100000 ? page : '1';
  const valid = isRunId(runId) && isPatternId(patternId);
  const read = useWorkspaceRead(valid ? `pattern-detail:${runId}:${patternId}:${view}` : '', signal => runResultApi.detail(runId, patternId, signal, view));
  const data = read.loading ? null : read.value;
  const p = data?.pattern, provenance = data?.result.provenance;
  return <Stack spacing="lg" mb={30}>
    <Title order={2}>Chi tiết pattern</Title>
    <Group><Button component={Link} variant="subtle"
      to={isRunId(runId) ? '/admin/patterns?runId=' + runId + '&page=' + returnPage + (view === 'published' ? '&view=published' : '') : '/admin/patterns'}
      state={location.state}>Về Pattern Explorer</Button>
    <Button disabled={!valid || read.loading} onClick={read.refresh}>Làm mới chi tiết</Button></Group>
    {!valid && <Alert color="yellow" role="alert">Run ID hoặc Pattern ID chưa hợp lệ. Không thể đọc chi tiết.</Alert>}
    {valid && (view === 'published' ? <Alert color="teal">Kết quả đã công bố và kiểm chứng manifest.</Alert> :
      <Alert color="yellow">Kết quả staging — chưa xác minh tính đầy đủ, chưa đủ điều kiện bàn giao Combo.
      Kiểm tra nhất quán dữ liệu không thay thế xác minh độc lập từ đầu vào hoặc oracle.</Alert>)}
    {read.loading && <Text role="status">Đang tải chi tiết pattern…</Text>}
    {read.error && ([404, 409, 413].includes(read.error.status || 0) ? <Alert role="alert" color="yellow" title="Chưa thể đọc chi tiết">
      <Text>{read.error.status === 404 ? 'Pattern không tồn tại hoặc không thuộc run được yêu cầu.'
        : read.error.status === 413 ? 'Chi tiết vượt giới hạn đọc của máy chủ. Không có kết quả bị cắt được hiển thị.'
          : 'Run chưa cho phép đọc staging, hoặc kết quả không nhất quán hay chưa được hỗ trợ.'}</Text>
      {read.error.requestId && <Text size="xs">Mã yêu cầu: {read.error.requestId}</Text>}
      <Button variant="subtle" onClick={read.refresh}>Thử lại</Button>
    </Alert> : <ReadError retry={read.refresh} error={read.error}/>)}
    {data && p && <>
      <Paper component="section" aria-label="Tổng quan pattern" withBorder p="md" radius="md"><Stack spacing="sm">
        <Title order={3}>Tổng quan pattern</Title>
        <CopyValue label="Pattern ID" value={p.patternId}/>
        <CopyValue label="Canonical pattern" value={p.canonicalPattern}
          displayValue={p.canonicalPattern.length > 512 ? p.canonicalPattern.slice(0, 512) + '… (bản xem trước)' : p.canonicalPattern}/>
        <Text>Support tuyệt đối: {p.supportAbs} · Tỷ lệ support: {p.supportRatio}</Text>
        <Text>Pattern weight: {p.patternWeight} · Weighted support: {p.weightedSupport}</Text>
        <Text>{p.itemsetCount} itemset · {p.patternItemCount} item</Text>
        <details><summary>Thông tin lưu trữ pattern</summary><CopyValue label="SHA-256 pattern" value={p.canonicalPatternHash}/>
          <Text size="sm">Chính sách weight đã lưu: {p.patternWeightPolicy}</Text></details>
      </Stack></Paper>
      <PatternStructure itemsets={data.itemsets}/>
      {provenance && <Paper component="section" aria-label="Provenance lịch sử" withBorder p="md" radius="md"><Stack spacing="sm">
        <Title order={3}>Nguồn dữ liệu lịch sử</Title>
        <CopyValue label="Run ID" value={data.result.runId}/><CopyValue label="Snapshot ID" value={provenance.snapshotId}/>
        <CopyValue label="Conversion attempt" value={provenance.attemptId}/>
        <Text>{provenance.sequenceCount} chuỗi · {provenance.version} · {provenance.conversionFormatVersion}</Text>
        <CopyValue label="SHA-256 validation" value={provenance.validationSha256}/>
        <CopyValue label="SHA-256 SPMF" value={provenance.sequenceSha256}/><CopyValue label="SHA-256 mapping" value={provenance.mappingSha256}/>
        <Text size="sm" color="dimmed">{view === 'published'
          ? 'Provenance và cấu trúc thuộc kết quả đã kiểm chứng với manifest công bố. Đây không phải kiểm chứng độc lập thuật toán từ đầu vào.'
          : 'Đây là provenance đã ghi nhận cho run, không chứng minh tệp hiện tại còn tồn tại hoặc kết quả đã công bố đầy đủ. Detail và danh sách là các lần đọc riêng; dữ liệu staging có thể thay đổi giữa các lần đọc.'}</Text>
      </Stack></Paper>}
    </>}
  </Stack>;
}
