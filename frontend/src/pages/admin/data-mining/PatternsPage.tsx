import React, { useEffect, useState } from 'react';
import { Alert, Button, Group, NativeSelect, Paper, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { useQuery, useQueryClient } from 'react-query';
import { useSearchParams } from 'react-router-dom';
import { runResultApi, WdmApiError } from 'api/admin/data-mining';
import { isRunId } from 'api/admin/data-mining/mining-run-response';
import { authSession, useSharedAuth } from 'auth/session';
import AuthGate from 'auth/AuthGate';
import useTitle from 'hooks/use-title';
import { listMiningRuns } from 'tv3/pages/mining-run/MiningRun.api';

// Project A integration; the TV1 client and persisted values remain unchanged.
const published = 'published' as const;
let nextScope = 0;

function ReadError({ error, retry }: { error: unknown; retry: () => void }) {
  const api = error instanceof WdmApiError ? error : null;
  const message = api?.status === 401 ? 'Phiên đăng nhập đã hết hạn.'
    : api?.status === 403 ? 'Tài khoản không có quyền ADMIN.'
      : api?.status === 404 ? 'Không tìm thấy RUN hoặc pattern thuộc RUN này.'
        : api?.status === 409 ? 'Kết quả chưa được công bố hoặc không vượt qua kiểm chứng dữ liệu.'
          : api?.status === 413 ? 'Kết quả vượt giới hạn đọc của máy chủ.'
            : 'Chưa tải được kết quả. Vui lòng thử lại.';
  return <Alert color="red" role="alert"><Text>{message}</Text>
    {api?.requestId && <Text size="xs">Mã yêu cầu: {api.requestId}</Text>}
    <Button variant="subtle" onClick={retry}>Thử lại</Button>
  </Alert>;
}

export default function PatternsPage() {
  useTitle('Mẫu khai phá');
  const state = useSharedAuth();
  const client = useQueryClient();
  const [scope] = useState(() => ++nextScope);
  useEffect(() => {
    const clear = () => {
      void client.cancelQueries(['pattern-reader', scope]);
      client.removeQueries(['pattern-reader', scope]);
    };
    const unsubscribe = authSession.onClear(clear);
    return () => { unsubscribe(); clear(); };
  }, [client, scope]);
  if (state.status !== 'authenticated') return state.status === 'anonymous'
    ? <Alert role="alert">Vui lòng đăng nhập tài khoản quản trị để xem mẫu khai phá.</Alert> : <AuthGate/>;
  if (state.forbidden || !state.user?.roles.includes('ADMIN'))
    return <Alert color="red" role="alert">Tài khoản không có quyền ADMIN.</Alert>;
  return <PatternReader key={state.user.userId} cacheKey={['pattern-reader', scope, state.user.userId]}/>;
}

function PatternReader({ cacheKey }: { cacheKey: unknown[] }) {
  const [params, setParams] = useSearchParams();
  const runId = params.get('runId') || '';
  const rawPage = params.get('page') || '1';
  const validPage = /^[1-9][0-9]{0,5}$/.test(rawPage) && Number(rawPage) <= 100000;
  const page = validPage ? Number(rawPage) : 1; // Pattern pages are 1-based.
  const validRun = isRunId(runId);
  const [input, setInput] = useState(runId);
  const [inputError, setInputError] = useState(false);
  const [runPage, setRunPage] = useState(0); // TV3 RUN list is 0-based.
  const [selection, setSelection] = useState<{ runId: string; page: number; id: string } | null>(null);
  useEffect(() => { setInput(runId); setInputError(false); }, [runId]);
  const runs = useQuery([...cacheKey, 'runs', runPage],
    ({ signal }) => listMiningRuns({ page: runPage, size: 20, status: 'SUCCEEDED', algorithm: '' }, signal),
    { retry: false });
  const patterns = useQuery([...cacheKey, 'patterns', runId, page, published],
    ({ signal }) => runResultApi.patterns(runId, page, signal, published),
    { enabled: validRun && validPage, retry: false });
  const selected = selection?.runId === runId && selection.page === page ? selection.id : '';
  const detail = useQuery([...cacheKey, 'detail', runId, selected, published],
    ({ signal }) => runResultApi.detail(runId, selected, signal, published),
    { enabled: validRun && validPage && !!selected, retry: false });
  const result = patterns.data;
  const nextStart = String(page * 20);
  const hasNext = !!result && (result.total.length > nextStart.length
    || (result.total.length === nextStart.length && result.total > nextStart));
  const move = (id: string, p = 1) => {
    setSelection(null);
    setParams({ runId: id, page: String(p), view: published });
  };
  const options = (runs.data?.items || []).map(run => ({
    value: run.runId, label: run.algorithm + ' · ' + run.runId,
  }));
  if (validRun && !options.some(o => o.value === runId)) options.push({ value: runId, label: runId });
  return <Stack spacing="lg">
    <Title order={2}>Mẫu khai phá</Title>
    <Text color="dimmed">Đọc kết quả đã công bố và kiểm chứng manifest. Các giá trị support và weight được giữ từ API.</Text>
    <Paper withBorder p="md"><Stack>
      {runs.isLoading && <Text role="status">Đang tải danh sách RUN…</Text>}
      {runs.isError && <ReadError error={runs.error} retry={() => { void runs.refetch(); }}/>}
      {runs.data?.items.length === 0 && <Text role="status">Chưa có RUN thành công trên trang này.</Text>}
      <NativeSelect label="Chọn RUN đã hoàn tất" value={validRun ? runId : ''}
        data={[{ value: '', label: 'Chọn RUN' }, ...options]}
        onChange={event => move(event.currentTarget.value)}/>
      <Group>
        <Button disabled={runPage === 0 || runs.isFetching} onClick={() => setRunPage(runPage - 1)}>Trang RUN trước</Button>
        <Text>Trang RUN {runPage + 1}</Text>
        <Button disabled={!runs.data || runPage + 1 >= runs.data.totalPages || runs.isFetching}
          onClick={() => setRunPage(runPage + 1)}>Trang RUN sau</Button>
      </Group>
      <form onSubmit={event => {
        event.preventDefault();
        if (!isRunId(input.trim())) { setInputError(true); return; }
        setInputError(false); move(input.trim());
      }}><Group align="end">
          <TextInput label="RUN ID" value={input} onChange={event => setInput(event.currentTarget.value)}
            error={inputError ? 'Nhập UUID chữ thường hợp lệ.' : undefined} style={{ flex: 1 }}/>
          <Button type="submit">Mở kết quả</Button>
        </Group></form>
    </Stack></Paper>
    {runId && !validRun && <Alert color="yellow" role="alert">RUN ID trong URL không hợp lệ.</Alert>}
    {!validPage && <Alert color="yellow" role="alert">Trang pattern phải từ 1 đến 100000.</Alert>}
    {!runId && <Text>Chọn một RUN để xem kết quả đã công bố.</Text>}
    {validRun && validPage && <>
      {patterns.isLoading && <Text role="status">Đang tải patterns đã công bố…</Text>}
      {patterns.isError && <ReadError error={patterns.error} retry={() => { void patterns.refetch(); }}/>}
      {result && <>
        <Text>RUN: {result.result.runId}</Text>
        <Text>Publication: {result.result.publicationStatus} · {result.result.completeness}</Text>
        <Text>Snapshot: {result.result.provenance?.snapshotId ?? 'Chưa có'}</Text>
        <Text>Tổng patterns: {result.total}</Text>
        {!result.items.length ? <Text role="status">{result.total === '0'
          ? 'Kết quả đã công bố có 0 pattern.' : 'Trang này không có pattern. Hãy về trang đầu.'}</Text>
          : <div style={{ overflowX: 'auto' }}><Table aria-label="Patterns đã công bố" striped>
            <thead><tr><th>Pattern</th><th>Support tuyệt đối</th><th>Tỷ lệ support</th><th>Weighted support</th>
              <th>Pattern weight</th><th>Itemsets / items</th><th>Chi tiết</th></tr></thead>
            <tbody>{result.items.map(pattern => <tr key={pattern.patternId}>
              <td><code style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{pattern.canonicalPattern}</code>
                <Text size="xs">ID: {pattern.patternId}</Text></td>
              <td>{pattern.supportAbs}</td><td>{pattern.supportRatio}</td><td>{pattern.weightedSupport}</td>
              <td>{pattern.patternWeight}</td><td>{pattern.itemsetCount} / {pattern.patternItemCount}</td>
              <td><Button size="xs" onClick={() => setSelection({ runId, page, id: pattern.patternId })}
                aria-label={'Xem pattern ' + pattern.patternId}>Xem chi tiết</Button></td>
            </tr>)}</tbody>
          </Table></div>}
        <Group>
          <Button disabled={page === 1} onClick={() => move(runId)}>Trang pattern đầu</Button>
          <Button disabled={page === 1} onClick={() => move(runId, page - 1)}>Trang pattern trước</Button>
          <Text>Trang pattern {page}</Text>
          <Button disabled={!hasNext || page === 100000} onClick={() => move(runId, page + 1)}>Trang pattern sau</Button>
        </Group>
        <Text size="sm">Thứ tự: support giảm dần → pattern weight giảm dần → ID giảm dần.</Text>
      </>}
      {selected && <Paper withBorder p="md" component="section" aria-label="Chi tiết pattern"><Stack>
        <Group><Title order={3}>Chi tiết pattern</Title><Button variant="subtle" onClick={() => setSelection(null)}>Đóng chi tiết</Button></Group>
        {detail.isLoading && <Text role="status">Đang tải chi tiết pattern…</Text>}
        {detail.isError && <ReadError error={detail.error} retry={() => { void detail.refetch(); }}/>}
        {detail.data && <>
          <Text>ID: {detail.data.pattern.patternId}</Text><code>{detail.data.pattern.canonicalPattern}</code>
          <Text>Chính sách weight: {detail.data.pattern.patternWeightPolicy}</Text>
          {detail.data.itemsets.map(itemset => <div key={itemset.itemsetOrder}>
            <Title order={4}>Itemset {itemset.itemsetOrder}</Title>
            <div style={{ overflowX: 'auto' }}><Table aria-label={'Itemset ' + itemset.itemsetOrder}>
              <thead><tr><th>Item ID</th><th>Product ID</th><th>Tên snapshot</th><th>SKU snapshot</th>
                <th>Item weight</th><th>Occurrences</th><th>Sequence support</th></tr></thead>
              <tbody>{itemset.items.map(item => <tr key={item.itemOrder}>
                <td>{item.itemId}</td><td>{item.productId}</td><td>{item.productNameSnapshot}</td><td>{item.skuSnapshot}</td>
                <td>{item.itemWeight}</td><td>{item.occurrenceCount}</td><td>{item.sequenceSupport}</td>
              </tr>)}</tbody>
            </Table></div>
          </div>)}
        </>}
      </Stack></Paper>}
    </>}
  </Stack>;
}
