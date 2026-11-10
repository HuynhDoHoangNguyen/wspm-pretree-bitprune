import React, { useEffect, useState } from 'react';
import { Alert, Button, Group, Paper, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { Link, useLocation, useSearchParams } from 'react-router-dom';
import { runResultApi } from 'api/admin/data-mining';
import { isRunId } from 'api/admin/data-mining/mining-run-response';
import useTitle from 'hooks/use-title';
import { useWorkspaceRead } from './useWorkspaceRead';
import { ReadError } from './WorkspacePanels';
import RunResultExportButton from './RunResultExportButton';

export default function PatternsPage() {
  useTitle('Mẫu khai phá');
  const [params, setParams] = useSearchParams();
  const location = useLocation();
  const origin = (location.state as { runReturnTo?: { returnTo?: string } } | null)?.runReturnTo;
  const history = typeof origin?.returnTo === 'string' && origin.returnTo.startsWith('/admin/mining-runs?')
    ? origin.returnTo : '/admin/mining-runs?tab=history';
  const runId = params.get('runId') || '';
  const requestedView = params.get('view') === 'published' ? 'published' : params.get('view') === 'staging' ? 'staging' : 'auto';
  const rawPage = params.get('page') || '1';
  const page = /^[1-9][0-9]{0,5}$/.test(rawPage) && Number(rawPage) <= 100000 ? Number(rawPage) : 1;
  const [input, setInput] = useState(runId);
  const [inputError, setInputError] = useState(false);
  useEffect(() => { setInput(runId); setInputError(false); }, [runId]);
  const valid = isRunId(runId);
  const read = useWorkspaceRead(valid ? `patterns:${runId}:${page}:${requestedView}` : '', async signal => {
    let selected: 'auto' | 'published' | 'staging' = requestedView;
    if (selected === 'auto') {
      const metadata = await runResultApi.metadata(runId, signal);
      selected = metadata.publishedReadable ? 'published' : 'staging';
    }
    if (signal.aborted) throw new Error('Read aborted');
    return runResultApi.patterns(runId, page, signal, selected);
  });
  const result = read.loading ? null : read.value;
  const view = result ? (result.result.publishedReadable ? 'published' : 'staging') : requestedView === 'auto' ? null : requestedView;
  const move = (next: number) => setParams({ runId, page: String(next), ...(view ? { view } : {}) }, { state: location.state });
  const nextStart = String(page * 20);
  const hasNext = !!result && (result.total.length > nextStart.length || (result.total.length === nextStart.length && result.total > nextStart));
  return <Stack spacing="lg" mb={30}>
    <Title order={2}>Mẫu khai phá</Title>
    <Text color="dimmed">Duyệt các mẫu đã lưu theo từng lần chạy khai phá.</Text>
    <Group>
      <Button component={Link} to={history} variant="subtle">Lịch sử run</Button>
      {valid && <Button component={Link} to={'/admin/mining-runs/' + runId}
        state={origin} variant="light">Chi tiết run</Button>}
    </Group>
    <Paper withBorder p="md" radius="md"><form onSubmit={event => {
      event.preventDefault();
      if (!isRunId(input.trim())) { setInputError(true); return; }
      setInputError(false); setParams({ runId: input.trim(), page: '1' });
    }}><Group align="flex-start">
        <TextInput label="Run ID" value={input} onChange={event => setInput(event.currentTarget.value)}
          error={inputError ? 'Nhập UUID chữ thường hợp lệ.' : undefined} style={{ flex: 1, minWidth: 220 }}/>
        <Button type="submit" mt={25}>Mở kết quả</Button>
      </Group></form></Paper>
    {!runId && <Text>Chọn run từ lịch sử hoặc nhập Run ID. Trang tự chọn kết quả đã công bố khi server xác minh được.</Text>}
    {!!runId && !valid && <Alert color="yellow" role="alert">Run ID trên URL chưa hợp lệ. Nhập UUID chữ thường để tiếp tục.</Alert>}
    {valid && <>
      {view === 'published' ? <Alert color="teal">Kết quả đã công bố; API kiểm chứng manifest trước khi đọc.</Alert> :
        view === 'staging' ? <Alert color="yellow">Kết quả staging — chưa xác minh tính đầy đủ, chưa đủ điều kiện bàn giao Combo.
        Run hoàn tất không chứng minh pattern đã được lưu đầy đủ.</Alert> : null}
      {result && view && <RunResultExportButton key={runId + view} runId={runId} view={view}/>}
      <Group position="apart"><Text size="sm" style={{ overflowWrap: 'anywhere' }}>Run: {runId}</Text>
        <Button onClick={read.refresh} disabled={read.loading}>Làm mới danh sách</Button></Group>
      {read.loading && <Text role="status">Đang tải pattern…</Text>}
      {read.error && (read.error.status === 409 ? <Alert color="yellow" role="alert" title="Chưa thể đọc kết quả Pattern">
        <Text>Chế độ đọc chưa khả dụng, hoặc metadata/kết quả chưa nhất quán. Kiểm tra trạng thái công bố trước khi tiếp tục.</Text>
        {read.error.requestId && <Text size="xs">Mã yêu cầu: {read.error.requestId}</Text>}
        <Button variant="subtle" onClick={read.refresh}>Thử lại</Button>
        {requestedView === 'staging' && <Button variant="subtle" onClick={() => setParams({ runId, page: '1' }, { state: location.state })}>Tự chọn kết quả theo trạng thái công bố</Button>}
        <Button component={Link} to={'/admin/mining-runs/' + runId} variant="subtle">Kiểm tra trạng thái công bố tại chi tiết run</Button>
      </Alert> : <ReadError error={read.error} retry={read.refresh}/>)}
      {result && <>
        <Text>Pattern đã lưu tại thời điểm đọc: <strong>{result.total}</strong></Text>
        {!result.items.length ? <Text role="status">{page === 1
          ? (view === 'published' ? 'Kết quả đã công bố có 0 pattern.' : 'Chưa có pattern trong staging tại thời điểm đọc. Điều này không chứng minh engine tìm được 0 pattern.')
          : 'Trang này không có pattern. Dữ liệu staging có thể đã thay đổi; hãy về trang đầu.'}</Text>
          : <div style={{ overflowX: 'auto' }}><Table striped highlightOnHover aria-label={view === 'published' ? 'Pattern đã công bố' : 'Pattern staging'} style={{ minWidth: 760 }}>
            <thead><tr><th>Pattern (mã item)</th><th>Support tuyệt đối</th><th>Tỷ lệ support</th><th>Pattern weight</th><th>Weighted support</th><th>Cấu trúc</th></tr></thead>
            <tbody>{result.items.map(p => <tr key={p.patternId}>
              <td><Text component="code" style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{p.canonicalPattern}</Text>
                <Text size="xs" color="dimmed">ID: {p.patternId}</Text>
                <Button component={Link} size="xs" variant="subtle" aria-label={'Xem chi tiết pattern ' + p.patternId}
                  to={'/admin/patterns/' + runId + '/' + p.patternId + '?page=' + page + (view === 'published' ? '&view=published' : '')} state={location.state}>Xem chi tiết</Button></td>
              <td>{p.supportAbs}</td><td>{p.supportRatio}</td><td>{p.patternWeight}</td><td>{p.weightedSupport}</td>
              <td>{p.itemsetCount} itemset · {p.patternItemCount} item</td>
            </tr>)}</tbody>
          </Table></div>}
        <Group><Button variant="default" disabled={page === 1} onClick={() => move(1)}>Trang đầu</Button>
          <Button variant="default" disabled={page === 1} onClick={() => move(page - 1)}>Trang trước</Button>
          <Text>Trang {page}</Text><Button variant="default" disabled={!hasNext || page === 100000} onClick={() => move(page + 1)}>Trang sau</Button></Group>
        <Text size="xs" color="dimmed">Thứ tự: support giảm dần → weight giảm dần → ID giảm dần.
          {view === 'published' ? ' Kết quả mỗi lần đọc được kiểm chứng với manifest công bố.' : ' Các trang được đọc riêng; staging có thể thay đổi giữa các lần đọc. Trang danh sách staging chưa xác minh temporal rows.'}
          Canonical giữ ranh giới itemset. Mở chi tiết để xem item và tên/SKU snapshot.</Text>
        <Text size="xs" color="dimmed">Chính sách weight: EQ2_EQ3_ARITHMETIC_MEAN_V1. Các giá trị được hiển thị từ API.</Text>
      </>}
    </>}
  </Stack>;
}
