import React, { useEffect, useRef, useState } from 'react';
import { Alert, Badge, Button, Group, NativeSelect, Paper, SimpleGrid, Skeleton, Stack, Table, Text, Title } from '@mantine/core';
import { MiningEmptyState, useMiningAdminStyles } from '../admin/data-mining/MiningAdminUi';
import { PatternClient, PatternClientError, PatternDetail, PatternPage, PatternSummary,
  PatternFilters, WeightCapability, RunOption } from './PatternClient';
import { patternHttpClient } from './PatternHttpClient';
import PatternFiltersPanel from './PatternFiltersPanel';

const metric = (value: number | null) => value === null || !Number.isFinite(value) ? 'Không khả dụng' : String(value);
function errorText(error: unknown): string {
  if (error instanceof PatternClientError) {
    if (error.code === 'API_NOT_AVAILABLE') return 'Chưa thể kết nối: API chọn run và API Pattern có quyền Admin phía server chưa sẵn sàng.';
    if (error.kind === 'aborted') return 'Yêu cầu đã bị hủy.';
    if (error.status === 401) return 'Vui lòng đăng nhập lại.';
    if (error.status === 403) return 'Bạn không có quyền Admin để đọc Pattern.';
    if (error.status === 404) return 'Không tìm thấy run hoặc Pattern trong run đã chọn.';
    if (error.status === 409) return 'Kết quả chưa khả dụng hoặc dữ liệu không nhất quán. Hãy tải lại.';
  }
  return 'Không tải được dữ liệu. Vui lòng thử lại.';
}
function Metrics({ pattern }: { pattern: PatternSummary }) {
  const { classes } = useMiningAdminStyles();
  return <Stack spacing="md">
    <Paper withBorder p="md" radius="sm"><Text size="xs" color="dimmed" mb={4}>Cấu trúc mẫu</Text><Text className={classes.code}>Canonical: {pattern.canonicalPattern}</Text></Paper>
    <SimpleGrid cols={3} spacing="sm" breakpoints={[{ maxWidth: 'md', cols: 2 }, { maxWidth: 'xs', cols: 1 }]}>
      <Text>Support (sequence): {pattern.supportAbs}</Text>
      <Text>Support ratio: {pattern.supportRatio === null || !Number.isFinite(pattern.supportRatio)
        ? 'Không khả dụng' : `${pattern.supportRatio * 100}%`}</Text>
      <Text>Weighted support: {metric(pattern.weightedSupport)}</Text>
      <Text>Trọng số mẫu: {metric(pattern.patternWeight)}</Text>
      <Text className={classes.wrap}>Policy: {pattern.patternWeightPolicy ?? 'Không khả dụng'}</Text>
      <Text>Số item: {pattern.patternItemCount}; số itemset: {pattern.itemsetCount}</Text>
    </SimpleGrid>
  </Stack>;
}

export default function PatternExplorer({ client = patternHttpClient }: { client?: PatternClient }) {
  const { classes } = useMiningAdminStyles();
  const [runs, setRuns] = useState<RunOption[]>([]);
  const [runPage, setRunPage] = useState(0);
  const [runTotal, setRunTotal] = useState<string | null>(null);
  const [runsError, setRunsError] = useState('');
  const [runsLoading, setRunsLoading] = useState(true);
  const [runId, setRunId] = useState('');
  const [page, setPage] = useState(0);
  const [list, setList] = useState<PatternPage | null>(null);
  const [listError, setListError] = useState('');
  const [listLoading, setListLoading] = useState(false);
  const [selected, setSelected] = useState('');
  const [detail, setDetail] = useState<PatternDetail | null>(null);
  const [detailError, setDetailError] = useState('');
  const [detailLoading, setDetailLoading] = useState(false);
  const [runsRetry, setRunsRetry] = useState(0);
  const [listRetry, setListRetry] = useState(0);
  const [detailRetry, setDetailRetry] = useState(0);
  const generation = useRef(0);
  const runsGeneration = useRef(0);
  const detailGeneration = useRef(0);
  const [applied, setApplied] = useState<PatternFilters>({});
  const [capability, setCapability] = useState<WeightCapability>({ enabled: false, reason: 'Chưa có xác nhận từ server', policy: null });
  const [filterError, setFilterError] = useState('');
  const [recoveryNotice, setRecoveryNotice] = useState('');
  const recovered = useRef(false);

  useEffect(() => {
    const token = ++runsGeneration.current;
    const controller = new AbortController();
    let current = true;
    setRunsLoading(true); setRunsError(''); setRuns([]); setRunTotal(null);
    client.runs(runPage, 20, { signal: controller.signal }).then(value => {
      if (!current || controller.signal.aborted || runsGeneration.current !== token) return;
      if (value.page !== runPage || value.size !== 20 || !/^\d+$/.test(value.total)) throw new Error('Invalid run page');
      setRuns(value.items); setRunTotal(value.total);
    }).catch(error => { if (current && !controller.signal.aborted && runsGeneration.current === token) setRunsError(errorText(error)); })
      .finally(() => { if (current && !controller.signal.aborted && runsGeneration.current === token) setRunsLoading(false); });
    return () => { current = false; controller.abort(); };
  }, [client, runPage, runsRetry]);

  useEffect(() => {
    const token = ++generation.current;
    const controller = new AbortController();
    let active = true;
    if (!runId) return;
    setListLoading(true); setListError(''); setList(null);
    function recoverWeight(reason: string) {
      if (recovered.current || (applied.weightMin === undefined && applied.weightMax === undefined)) return false;
      recovered.current = true;
      generation.current++; detailGeneration.current++;
      setSelected(''); setDetail(null); setDetailError(''); setDetailLoading(false);
      const remaining = { ...applied }; delete remaining.weightMin; delete remaining.weightMax;
      setCapability({ enabled: false, reason, policy: null });
      setRecoveryNotice(`Đã bỏ cận trọng số và tải lại: ${reason}`);
      setApplied(remaining); setPage(0);
      return true;
    }
    const request = client.list(runId, page, 20, Object.keys(applied).length ? applied : undefined, { signal: controller.signal });
    request.then(value => {
      if (!active || controller.signal.aborted || generation.current !== token) return;
      if (value.runId !== runId || value.page !== page || value.size !== 20) throw new Error('Mismatched response');
      if (value.resultState === 'READY' && (value.filteredTotal === null || value.runPatternTotal === null
        || !/^\d+$/.test(value.filteredTotal) || !/^\d+$/.test(value.runPatternTotal))) throw new Error('Invalid totals');
      const weight = value.weightFilter ?? { enabled: false, reason: 'Chưa có xác nhận từ server', policy: null };
      setCapability(weight);
      if (!weight.enabled && recoverWeight(weight.reason ?? 'WEIGHT_FILTER_UNAVAILABLE')) return;
      setList(value);
    }).catch(error => {
      if (!active || controller.signal.aborted || generation.current !== token) return;
      if (error instanceof PatternClientError && error.code === 'WEIGHT_FILTER_UNAVAILABLE') {
        if (recoverWeight(error.code)) return;
        setFilterError('WEIGHT_FILTER_UNAVAILABLE: không thể áp dụng trọng số.');
      } else if (error instanceof PatternClientError && ['INVALID_FILTER', 'INVALID_QUERY', 'ITEM_NOT_IN_RUN_DICTIONARY'].includes(error.code)) {
        setFilterError(`${error.code}: kiểm tra sản phẩm và cận đang nhập, rồi áp dụng lại.${error.offendingItemIds.length ? ' Item: ' + error.offendingItemIds.join(', ') : ''}`);
      } else setListError(errorText(error));
    })
      .finally(() => { if (active && !controller.signal.aborted && generation.current === token) setListLoading(false); });
    return () => { active = false; controller.abort(); };
  }, [client, runId, page, listRetry, applied]);

  useEffect(() => {
    const token = ++detailGeneration.current;
    const controller = new AbortController();
    let active = true;
    if (!selected || !runId) return;
    setDetailLoading(true); setDetailError(''); setDetail(null);
    client.detail(runId, selected, { signal: controller.signal }).then(value => {
      if (!active || controller.signal.aborted || detailGeneration.current !== token) return;
      if (value.runId !== runId || value.patternId !== selected) throw new Error('Mismatched detail');
      setDetail(value);
    }).catch(error => { if (active && !controller.signal.aborted && detailGeneration.current === token) setDetailError(errorText(error)); })
      .finally(() => { if (active && !controller.signal.aborted && detailGeneration.current === token) setDetailLoading(false); });
    return () => { active = false; controller.abort(); };
  }, [client, runId, selected, detailRetry]);

  function closeDetail() {
    detailGeneration.current++; setSelected(''); setDetail(null); setDetailError(''); setDetailLoading(false);
  }
  function changeRun(value: string) {
    generation.current++; closeDetail(); setList(null); setListError(''); setListLoading(false);
    setRunId(value); setPage(0);
    setApplied({}); setFilterError(''); setRecoveryNotice(''); recovered.current = false;
    setCapability({ enabled: false, reason: 'Chưa có xác nhận từ server', policy: null });
  }
  function applyFilters(value: PatternFilters) {
    generation.current++; closeDetail(); setList(null); setListError(''); setFilterError('');
    setRecoveryNotice(''); recovered.current = false; setApplied(value); setPage(0);
  }
  function changePage(value: number) { generation.current++; closeDetail(); setList(null); setPage(value); }
  function changeRunPage(value: number) {
    runsGeneration.current++; changeRun(''); setRuns([]); setRunTotal(null); setRunPage(value);
  }
  const next = list?.resultState === 'READY' && list.filteredTotal !== null
    && BigInt(list.filteredTotal) > BigInt(page + 1) * BigInt(20) && page < 2147483647;
  return <Stack spacing="lg" className={classes.page}>
    <Stack spacing={4} className={classes.header}>
      <Text size="sm" color="dimmed">Dữ liệu &amp; Khai phá</Text>
      <Title order={2}>Khám phá Pattern</Title>
      <Text color="dimmed">Tìm mẫu mua hàng, lọc sản phẩm và xem thứ tự xuất hiện trong từng mẫu.</Text>
    </Stack>
    <Paper withBorder className={classes.card}>
      <Stack spacing="md">
        <Group position="apart"><Title order={3}>Chọn lần khai phá</Title>
          {!runsLoading && !runsError && runTotal !== '0' && <Badge variant="light">Tổng mining run: {runTotal ?? 'Chưa khả dụng'}</Badge>}
        </Group>
        <NativeSelect label="Mining run" description="Danh sách các lần khai phá đã được lưu." size="sm"
          value={runId} disabled={runsLoading || !!runsError || !runs.length}
          onChange={e => changeRun(e.target.value)}
          data={[{ value: '', label: 'Chọn run' }, ...runs.map(r => ({ value: r.runId, label: r.label }))]}/>
        {runsLoading && <Stack spacing="xs" role="status"><Text size="sm" color="dimmed">Đang tải danh sách run…</Text><Skeleton height={52}/></Stack>}
        {runsError && <Alert color="red" role="alert">{runsError}<Group mt="sm"><Button variant="light" onClick={() => setRunsRetry(x => x + 1)}>Thử lại kết nối</Button></Group></Alert>}
        {!runId && !runsError && !runsLoading && <MiningEmptyState title={runTotal === '0' ? 'Chưa có mining run.'
          : runs.length ? 'Chọn một run để xem Pattern.' : 'Trang run hiện tại không có dữ liệu.'}>
          {runTotal === '0' ? 'Các lần khai phá được lưu sẽ xuất hiện tại đây. Hãy quay lại khi có kết quả mới.'
            : 'Chọn một lần khai phá trong danh sách phía trên để bắt đầu khám phá.'}
        </MiningEmptyState>}
        {!runsError && !runsLoading && runTotal !== '0' && <Group position="apart" className={classes.footer}>
          <Text size="sm" color="dimmed">Trang run {runPage + 1}</Text>
          <Group spacing="xs"><Button variant="default" disabled={runPage === 0} onClick={() => changeRunPage(runPage - 1)}>Run trước</Button>
            <Button variant="default" disabled={runTotal === null || BigInt(runTotal) <= BigInt(runPage + 1) * BigInt(20) || runPage >= 2147483647}
              onClick={() => changeRunPage(runPage + 1)}>Run sau</Button></Group>
        </Group>}
      </Stack>
    </Paper>
    {runId && <>
      <PatternFiltersPanel key={runId} client={client} runId={runId} capability={capability} serverError={filterError} onApply={applyFilters}/>
      <Text size="sm" color="dimmed" className={classes.wrap}>Bộ lọc đã áp dụng — item: {applied.itemIds?.join(', ') || 'Tất cả'};
        support: {applied.supportMin ?? 'Không cận dưới'} đến {applied.supportMax ?? 'Không cận trên'};
        trọng số: {applied.weightMin ?? 'Không cận dưới'} đến {applied.weightMax ?? 'Không cận trên'}</Text>
      {recoveryNotice && <Alert color="yellow" role="status">{recoveryNotice}</Alert>}
      <Paper withBorder className={classes.card}>
        <Stack spacing="md">
          <Group position="apart"><Title order={3}>Danh sách Pattern</Title>
            {list && <Badge variant="light">{list.runStatus}</Badge>}
          </Group>
          {listLoading && <Stack spacing="sm" role="status"><Text color="dimmed">Đang tải Pattern…</Text>{[1, 2, 3].map(n => <Skeleton key={n} height={44}/>)}</Stack>}
          {listError && <Alert color="red" role="alert">{listError}<Group mt="sm"><Button variant="light" onClick={() => setListRetry(x => x + 1)}>Tải lại danh sách</Button></Group></Alert>}
          {list && <>
            <Text size="xs" color="dimmed" className={classes.wrap}>Run: {list.runId} — {list.runStatus}</Text>
            <Text size="sm" color="dimmed">Tổng Pattern của run: {list.runPatternTotal ?? 'Chưa khả dụng'}; tổng khớp: {list.filteredTotal ?? 'Chưa khả dụng'}; số dòng trang: {list.items.length}</Text>
            {list.resultState === 'NOT_READY' && <MiningEmptyState title="Run đang chờ hoặc đang chạy, chưa có kết quả sử dụng được.">Bạn có thể xem kết quả sau khi lần khai phá hoàn tất.</MiningEmptyState>}
            {list.resultState === 'FAILED' && <Alert color="red" role="alert">Run thất bại.</Alert>}
            {list.resultState === 'UNVERIFIED' && <Alert color="yellow" role="status">Kết quả run chưa được xác minh.</Alert>}
            {list.resultState === 'READY' && <>
              {list.runPatternTotal === '0' ? <MiningEmptyState title="Run thành công, không có Pattern.">Lần khai phá này không có mẫu phù hợp.</MiningEmptyState> : <>
                {list.filteredTotal === '0' ? <MiningEmptyState title="Không có Pattern khớp bộ lọc.">Thử bỏ bớt sản phẩm hoặc mở rộng khoảng giá trị.</MiningEmptyState>
                  : list.items.length === 0 && <MiningEmptyState title="Trang hiện tại không có Pattern.">Quay về trang trước để tiếp tục xem kết quả.</MiningEmptyState>}
                {list.items.length > 0 && <div className={classes.scroll} role="region" aria-label="Bảng Pattern" tabIndex={0}>
                  <Table highlightOnHover className={classes.table}><thead><tr><th>Pattern ID</th><th>Cấu trúc mẫu</th><th>Support</th></tr></thead>
                    <tbody>{list.items.map(p => <tr key={p.patternId} data-selected={selected === p.patternId}>
                      <td><Button variant="subtle" className={classes.rowButton} aria-pressed={selected === p.patternId} onClick={() => {
                        if (selected === p.patternId) return;
                        detailGeneration.current++; setDetail(null); setDetailError(''); setSelected(p.patternId);
                      }}>{p.patternId}</Button></td><td className={classes.code}>{p.canonicalPattern}</td><td className={classes.wrap}>{p.supportAbs}</td>
                    </tr>)}</tbody>
                  </Table>
                </div>}
              </>}
              {(page > 0 || (list.filteredTotal !== null && BigInt(list.filteredTotal) > BigInt(0))) && <Group position="apart" className={classes.footer}>
                <Text size="sm" color="dimmed">Trang {page + 1}</Text><Group spacing="xs">
                  <Button variant="default" disabled={page === 0} onClick={() => changePage(page - 1)}>Trang trước</Button>
                  <Button variant="default" disabled={!next} onClick={() => changePage(page + 1)}>Trang sau</Button>
                </Group>
              </Group>}
            </>}
          </>}
        </Stack>
      </Paper>
    </>}
    {selected && <Paper withBorder className={classes.card}><Stack spacing="lg">
      <Group position="apart" align="flex-start"><Title order={3} className={classes.wrap}>Chi tiết Pattern {selected}</Title><Button variant="default" onClick={closeDetail}>Đóng chi tiết</Button></Group>
      {detailLoading && <Stack role="status"><Text color="dimmed">Đang tải chi tiết…</Text><Skeleton height={100}/></Stack>}
      {detailError && <Alert color="red" role="alert">{detailError}<Group mt="sm"><Button variant="light" onClick={() => setDetailRetry(x => x + 1)}>Tải lại chi tiết</Button></Group></Alert>}
      {detail && <>
        <Metrics pattern={detail}/><Text size="sm" color="dimmed" className={classes.wrap}>Số sequence của run: {detail.sequenceCount ?? 'Không khả dụng'}</Text>
        {detail.integrityStatus === 'INCOMPLETE' && <Alert color="yellow" role="status">Dữ liệu Pattern chưa đầy đủ (INCOMPLETE).</Alert>}
        {detail.issues.map((issue, index) => <Text size="sm" color="red" className={classes.wrap} key={index}>{issue.code} — nhóm {issue.itemsetOrder ?? '—'}, vị trí {issue.itemOrder ?? '—'}, item {issue.itemId ?? '—'}</Text>)}
        <div><Title order={4} mb="sm">Sản phẩm theo thứ tự</Title><Stack spacing="sm">
          {detail.itemsets.map(group => <Paper withBorder p="md" key={group.itemsetOrder} role="group" aria-label={'Itemset ' + group.itemsetOrder} className={classes.itemset}>
            <Badge variant="light" mb="sm">Itemset {group.itemsetOrder}</Badge>
            {group.items.map((item, index) => <Text className={classes.wrap} key={index}>Vị trí {item.itemOrder}: {item.mappingStatus === 'MISSING'
              ? 'Item #' + item.itemId + ' — thiếu dictionary của run'
              : (item.nameSnapshot ?? 'Thiếu tên snapshot') + ' (' + (item.skuSnapshot ?? 'Thiếu SKU snapshot') + ')'}</Text>)}
          </Paper>)}
        </Stack></div>
      </>}
    </Stack></Paper>}
  </Stack>;
}
