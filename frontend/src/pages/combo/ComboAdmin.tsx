import React, { useEffect, useRef, useState } from 'react';
import { Alert, Badge, Button, Group, NativeSelect, Paper, SimpleGrid, Skeleton, Stack, Table, Text, Textarea, TextInput, Title } from '@mantine/core';
import { MiningEmptyState, useMiningAdminStyles } from '../admin/data-mining/MiningAdminUi';
import { Audit, ComboClient, ComboClientError, ComboDetail, ComboFilters, ComboPage, Decision } from './ComboClient';
import { comboHttpClient } from './ComboHttpClient';
import PublishedComboInputPanel from './PublishedComboInputPanel';
import ComboBatchHistory from './ComboBatchHistory';
import { useInRouterContext, useSearchParams } from 'react-router-dom';

const statuses: Record<string, string> = { DRAFT: 'Bản nháp', APPROVED: 'Đã duyệt nội bộ', REJECTED: 'Đã từ chối', PUBLISHED: 'Đã công bố', ARCHIVED: 'Đã lưu trữ' };
const kinds = ['SAME_ORDER_BUNDLE', 'SEQUENTIAL_CROSS_SELL', 'MIXED_SEQUENCE'];
const kindLabel: Record<string, string> = { SAME_ORDER_BUNDLE: 'Mua cùng đơn', SEQUENTIAL_CROSS_SELL: 'Mua theo thứ tự', MIXED_SEQUENCE: 'Kết hợp nhóm & thứ tự' };
const blockers: Record<string, string> = {
  CATALOG_PRICE_OR_ELIGIBILITY_CHANGED: 'Giá, phiên bản hoặc trạng thái sản phẩm đã thay đổi. Cần tạo đề xuất mới; hệ thống không tự sửa giá Combo.',
  REVIEW_POLICY_UNCONFIRMED: 'Chưa có chính sách duyệt được xác nhận.',
  REVIEW_EVIDENCE_UNVERIFIED: 'Thông tin làm cơ sở duyệt chưa được xác minh.',
  WRITER_LOCK_PROTOCOL_UNCONFIRMED: 'Chưa sẵn sàng ghi quyết định an toàn.',
  ACTION_ELIGIBILITY_UNVERIFIED: 'Chưa đủ điều kiện thực hiện quyết định.',
  BATCH_NOT_REVIEWABLE: 'Batch không còn ở trạng thái cho phép duyệt.',
  RUN_NOT_REVIEWABLE: 'Lần khai phá chưa hoàn tất hợp lệ.',
  PRIMARY_SOURCE_INVALID: 'Chưa xác định được một nguồn chính hợp lệ.',
  PRIMARY_METRICS_MISMATCH: 'Chỉ số Combo chưa khớp nguồn chính.',
  DICTIONARY_SNAPSHOT_MISSING: 'Thiếu thông tin sản phẩm của lần khai phá.',
};
const statusColor = (status: string) => ({ DRAFT: 'gray', APPROVED: 'teal', REJECTED: 'red', PUBLISHED: 'blue', ARCHIVED: 'gray' } as Record<string, string>)[status] ?? 'orange';
const batchLabel = (value: string) => ({ DRAFT: 'Batch nháp', ACTIVE: 'Batch đang kích hoạt', ARCHIVED: 'Batch đã lưu trữ' } as Record<string, string>)[value] ?? `Batch không xác định: ${value}`;
const statusLabel = (value: string) => statuses[value] ?? `Không xác định: ${value}`;
function failure(error: unknown): string {
  if (error instanceof ComboClientError) {
    if (error.code === 'API_NOT_AVAILABLE') return 'Chưa thể kết nối: API Combo có quyền Admin phía server chưa sẵn sàng.';
    if (error.status === 401) return 'Vui lòng đăng nhập lại.';
    if (error.status === 403) return 'Bạn không có quyền Admin.';
    if (error.status === 404) return 'Không tìm thấy batch hoặc Combo.';
    return `Không tải được dữ liệu: ${error.code}`;
  }
  return 'Lỗi mạng hoặc tải dữ liệu. Hãy thử lại.';
}
const count = (s: string) => { if (!/^\d+$/.test(s)) throw new Error('INVALID_COUNT'); return BigInt(s); };
const invalidNoteCharacter = (c: string) => {
  const code = c.codePointAt(0) ?? 0;
  return (code < 32 && code !== 9 && code !== 10 && code !== 13) || (code >= 127 && code <= 159) || (code >= 55296 && code <= 57343);
};
function ReviewAudit({ audit }: { audit: Audit }) {
  const { classes } = useMiningAdminStyles();
  return <Stack spacing={4} className={classes.wrap}><Text size="sm">Người review: {audit.reviewerDisplayName ?? audit.reviewedBy ?? 'Chưa có'}{audit.reviewerDisplayName && audit.reviewedBy ? ` (${audit.reviewedBy})` : ''}</Text>
    <Text size="sm" color="dimmed">Thời điểm review (UTC): {audit.reviewedAt ?? 'Chưa có'}</Text><Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>Ghi chú: {audit.reviewNote ?? 'Không có'}</Text></Stack>;
}
export default function ComboAdmin({ client = comboHttpClient }: { client?: ComboClient }) {
  const routed=useInRouterContext();
  return routed ? <RoutedComboAdmin client={client}/> : <ComboAdminView client={client}/>;
}
function RoutedComboAdmin({ client }: { client: ComboClient }) {
  const [params,setParams]=useSearchParams(); const ids=params.getAll('batchId');
  const batch=ids.length===1 && ids[0].length<=40 ? ids[0] : undefined;
  const generation=params.get('tab')==='generate'||params.has('runId');
  if(client.batches) return <Stack spacing="lg" style={{ maxWidth: 1400, margin: '0 auto', paddingBottom: 32 }}>
    <Paper withBorder radius="md" p="xl" sx={theme=>({ backgroundColor: theme.colorScheme==='dark'?theme.colors.dark[6]:theme.colors.blue[0] })}>
      <Text size="xs" weight={700} color="blue" transform="uppercase">Đề xuất từ dữ liệu khai phá</Text>
      <Title order={2} mt={6}>Quản lý Combo</Title><Text color="dimmed" mt="xs">Mỗi lần lưu DRAFT được gom thành một batch. Mở batch để xem và duyệt từng đề xuất.</Text>
      <Group mt="lg"><Button variant={!generation?'filled':'white'} onClick={()=>setParams({})}>Lịch sử lưu DRAFT</Button>
        <Button variant={generation?'filled':'white'} onClick={()=>setParams({ tab: 'generate' })}>Tạo đề xuất Combo</Button></Group>
    </Paper>
    {generation ? <PublishedComboInputPanel/> : batch ? <>
      <Group position="apart"><Button variant="subtle" onClick={()=>setParams({})}>← Lịch sử batch</Button>
        <Text size="sm" color="dimmed" style={{ overflowWrap: 'anywhere' }}>Batch {batch}</Text></Group>
      <ComboAdminView key={batch} client={client} initialBatchId={batch} embedded/>
    </> : <ComboBatchHistory client={client} openBatch={id=>setParams({ batchId: id })}/>}
  </Stack>;
  return <ComboAdminView key={batch || ''} client={client} initialBatchId={batch}/>;
}
function ComboAdminView({ client, initialBatchId, embedded = false }: { client: ComboClient; initialBatchId?: string; embedded?: boolean }) {
  const { classes } = useMiningAdminStyles();
  const [draft, setDraft] = useState<ComboFilters>(initialBatchId ? { batchId: initialBatchId } : {});
  const [query, setQuery] = useState({ filters: (initialBatchId ? { batchId: initialBatchId } : {}) as ComboFilters, page: 0 });
  const [revision, setRevision] = useState(0);
  const [data, setData] = useState<ComboPage | null>(null);
  const [error, setError] = useState(''); const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const [detail, setDetail] = useState<ComboDetail | null>(null);
  const [detailError, setDetailError] = useState(''); const [detailLoading, setDetailLoading] = useState(false);
  const [note, setNote] = useState(''); const [decision, setDecision] = useState<Decision | null>(null);
  const [sending, setSending] = useState(false); const [notice, setNotice] = useState('');
  const [savedAudit, setSavedAudit] = useState<Audit | null>(null);
  const [reconciling, setReconciling] = useState(false);
  const listGeneration = useRef(0), detailGeneration = useRef(0), sendGeneration = useRef(0);
  const mounted = useRef(true);
  const selectedRef = useRef(selected); selectedRef.current = selected;
  const submitting = useRef(false), reviewAbort = useRef<AbortController | null>(null);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; reviewAbort.current?.abort(); }; }, []);
  const invalidate = () => { listGeneration.current++; detailGeneration.current++; setRevision(v => v + 1); };
  const changeQuery = (filters: ComboFilters, page: number) => {
    if (embedded && initialBatchId) filters = { ...filters, batchId: initialBatchId };
    sendGeneration.current++; reviewAbort.current?.abort(); submitting.current = false; setSending(false); setSavedAudit(null);
    listGeneration.current++; detailGeneration.current++; setSelected(null); setDetail(null); setDecision(null);
    setNote(''); setQuery({ filters, page });
  };
  useEffect(() => {
    const generation = ++listGeneration.current; let active = true; const controller = new AbortController();
    setLoading(true); setData(null); setError('');
    client.list(query.filters, query.page, 20, { signal: controller.signal }).then(result => {
      if (!active || generation !== listGeneration.current) return;
      const total = count(result.filteredTotal); count(result.scopeTotal);
      const last = total === BigInt(0) ? BigInt(0) : (total - BigInt(1)) / BigInt(20);
      if (BigInt(query.page) > last) { changeQuery(query.filters, Number(last)); return; }
      if (query.page > 0 && result.items.length === 0) { changeQuery(query.filters, 0); return; }
      if (selectedRef.current && !result.items.some(i => i.data.comboId === selectedRef.current)) { detailGeneration.current++; setSelected(null); setDetail(null); setDecision(null); }
      setData(result);
    }).catch(e => { if (active && generation === listGeneration.current) setError(failure(e)); })
      .finally(() => { if (active && generation === listGeneration.current) setLoading(false); });
    return () => { active = false; controller.abort(); };
  }, [client, query, revision]);
  useEffect(() => {
    const generation = ++detailGeneration.current; let active = true; const controller = new AbortController();
    setDetail(null); setDetailError(''); setDecision(null); setNote('');
    if (!selected) { setDetailLoading(false); return; }
    setDetailLoading(true);
    client.detail(selected, { signal: controller.signal }).then(result => {
      if (!active || generation !== detailGeneration.current) return;
      if (result.data.comboId !== selected) throw new Error('DETAIL_CONTEXT_MISMATCH');
      setDetail(result); setReconciling(false);
    }).catch(e => { if (active && generation === detailGeneration.current) setDetailError(failure(e)); })
      .finally(() => { if (active && generation === detailGeneration.current) setDetailLoading(false); });
    return () => { active = false; controller.abort(); };
  }, [client, selected, revision]);
  const notePolicy = detail?.notePolicy ?? client.notePolicy;
  const canReview = (action: Decision) => !!(detail && detail.data.comboStatus === 'DRAFT' && client.reviewReady
    && notePolicy?.confirmed && detail.reviewToken?.trim() && detail.assessment.allowedActions?.includes(action)
    && detail.assessment.blockingIssues.length === 0 && !sending && !reconciling && !detailLoading);
  const submit = async () => {
    if (submitting.current || !decision || !detail || !canReview(decision)) return;
    const policy = notePolicy, token = detail.reviewToken;
    if (!policy?.confirmed || !token) return;
    const normalized = note.trim() || null;
    if (decision === 'REJECT' && policy.rejectRequired && !normalized) { setNotice('Cần ghi chú từ chối.'); return; }
    if (normalized && (Array.from(normalized).length > policy.maxCodePoints || Array.from(normalized).some(invalidNoteCharacter))) {
      setNotice('Ghi chú không hợp lệ hoặc vượt giới hạn.'); return;
    }
    const generation = ++sendGeneration.current;
    submitting.current = true; const controller = new AbortController(); reviewAbort.current = controller;
    setSending(true); setNotice(''); setSavedAudit(null);
    try {
      const result = await client.review(detail.data.comboId, { decision, reviewNote: normalized, expectedReviewToken: token }, { signal: controller.signal });
      if (!mounted.current || generation !== sendGeneration.current) return;
      if (result.outcome === 'APPLIED' || result.outcome === 'ALREADY_DECIDED') {
        setNotice(result.outcome === 'APPLIED' ? 'Vừa xử lý thành công.' : 'Đã có quyết định này; giữ audit đã lưu.');
        setSavedAudit(result.storedAudit); invalidate();
      } else if (result.outcome === 'CONFLICT' || result.issues.includes('STALE_REVIEW_DATA')) {
        setNotice('Quyết định hoặc dữ liệu đã thay đổi. Đang tải lại.'); invalidate();
      } else if (result.outcome === 'UNKNOWN') {
        setNotice('Chưa rõ kết quả commit. Đang đọc lại trước khi thử lại.'); setReconciling(true); invalidate();
      } else { setNotice(`Chưa ghi quyết định: ${result.outcome}. ${result.issues.join(', ')}`); }
    } catch (e) {
      if (!mounted.current || generation !== sendGeneration.current) return;
      setNotice(e instanceof ComboClientError && e.status === 409 ? 'Quyết định hoặc dữ liệu đã thay đổi. Đang tải lại.' : failure(e) + ' Đang đọc lại trước khi thử lại.'); setReconciling(true); invalidate();
    } finally {
      if (mounted.current && generation === sendGeneration.current) { submitting.current = false; setSending(false); setDecision(null); }
    }
  };
  return <Stack spacing="lg" className={classes.page}>
    {!embedded && <PublishedComboInputPanel/>}
    {!embedded && <Stack spacing={4} className={classes.header}><Text size="sm" color="dimmed">Dữ liệu &amp; Khai phá</Text>
      <Title order={2}>Quản lý Combo</Title>
      <Text color="dimmed">Xem bộ sản phẩm, kiểm tra nguồn và duyệt các đề xuất.</Text>
    </Stack>}
    <Paper withBorder className={classes.card}>
      <form aria-label="Bộ lọc Combo" onSubmit={e => {
        e.preventDefault(); if (draft.batchId && (draft.batchId.trim() === '' || Array.from(draft.batchId).length > 40)) { setNotice('Batch ID không hợp lệ.'); return; }
        changeQuery(draft, 0);
      }}><Stack spacing="md">
          <Title order={3}>Bộ lọc Combo</Title>
          <SimpleGrid cols={embedded ? 2 : 3} spacing="md" breakpoints={[{ maxWidth: 'md', cols: 2 }, { maxWidth: 'sm', cols: 1 }]}>
            {!embedded && <TextInput label="Batch ID" placeholder="Nhập mã batch" size="sm" value={draft.batchId ?? ''} onChange={e => { const value = e.target.value; setDraft(d => ({ ...d, batchId: value || undefined })); }}/>}
            <NativeSelect label="Trạng thái Combo" size="sm" value={draft.status ?? ''} onChange={e => { const value = e.target.value; setDraft(d => ({ ...d, status: value || undefined })); }}
              data={[{ value: '', label: 'Tất cả' }, ...Object.keys(statuses).map(s => ({ value: s, label: statuses[s] }))]}/>
            <NativeSelect label="Loại Combo" size="sm" value={draft.comboKind ?? ''} onChange={e => { const value = e.target.value; setDraft(d => ({ ...d, comboKind: value || undefined })); }}
              data={[{ value: '', label: 'Tất cả' }, ...kinds.map(k => ({ value: k, label: kindLabel[k] }))]}/>
          </SimpleGrid>
          <Group className={classes.footer}><Button type="submit">Áp dụng bộ lọc</Button><Button variant="default" onClick={() => { setDraft({}); changeQuery({}, 0); }}>Bỏ lọc</Button></Group>
        </Stack></form>
    </Paper>
    <Paper withBorder className={classes.card}>
      <Stack spacing="md">
        <Group position="apart"><Title order={3}>Danh sách Combo</Title>{data && <Badge variant="light">{data.filteredTotal} kết quả</Badge>}</Group>
        {loading && <Stack spacing="sm" role="status"><Text color="dimmed">Đang tải danh sách…</Text>{[1, 2, 3].map(n => <Skeleton key={n} height={48}/>)}</Stack>}
        {error && <Alert color="red" role="alert">{error}<Group mt="sm"><Button variant="light" onClick={invalidate}>Thử lại danh sách</Button></Group></Alert>}
        {data && <>
          <Text size="sm" color="dimmed">Trong phạm vi: {data.scopeTotal}; Khớp bộ lọc: {data.filteredTotal}</Text>
          {data.items.length === 0 ? <MiningEmptyState title={count(data.scopeTotal) === BigInt(0) ? 'Chưa có Combo trong phạm vi đã chọn.' : 'Không có Combo khớp bộ lọc.'}>
            {count(data.scopeTotal) === BigInt(0) ? 'Các đề xuất Combo được lưu sẽ xuất hiện tại đây để bạn xem và duyệt.' : 'Thử thay đổi trạng thái, loại Combo hoặc bỏ bộ lọc.'}
          </MiningEmptyState> :
            <div className={classes.scroll} role="region" aria-label="Bảng Combo" tabIndex={0}>
              <Table highlightOnHover className={classes.table}><thead><tr><th>Combo</th><th>Loại</th><th>Trạng thái Combo</th><th>Batch</th><th>Review</th></tr></thead>
                <tbody>{data.items.map(s => <tr key={s.data.comboId} data-selected={selected === s.data.comboId}>
                  <td><Button variant="subtle" className={classes.rowButton} aria-label={s.data.name + ' (' + s.data.comboId + ')'} aria-pressed={selected === s.data.comboId} disabled={sending}
                    onClick={() => { if (selected === s.data.comboId) { invalidate(); return; } detailGeneration.current++; setSelected(s.data.comboId); setDetail(null); }}>
                    <Text component="span" weight={600}>{s.data.name}</Text><Text component="span" size="xs" color="dimmed" sx={{ display: 'block' }}>({s.data.comboId})</Text>
                  </Button></td>
                  <td><Badge variant="light" className={classes.tag}>{kindLabel[s.data.comboKind] ?? s.data.comboKind}</Badge></td>
                  <td><Badge color={statusColor(s.data.comboStatus)} variant="light" className={classes.tag}>{statusLabel(s.data.comboStatus)}</Badge></td>
                  <td className={classes.wrap}><Text size="xs" color="dimmed">{s.data.batchId}</Text><Text size="sm">{batchLabel(s.data.batchStatus)}</Text></td>
                  <td className={classes.wrap}><Text size="sm">{s.data.review.reviewerDisplayName ?? s.data.review.reviewedBy ?? 'Chưa review'}</Text>
                    {s.data.review.reviewedAt && <Text size="xs" color="dimmed">{s.data.review.reviewedAt}</Text>}</td>
                </tr>)}</tbody>
              </Table>
            </div>}
          {(query.page > 0 || data.items.length > 0) && <Group position="apart" className={classes.footer}>
            <Text size="sm" color="dimmed">Trang {query.page + 1}</Text><Group spacing="xs">
              <Button variant="default" disabled={query.page === 0} onClick={() => changeQuery(query.filters, query.page - 1)}>Trang trước</Button>
              <Button variant="default" disabled={query.page >= 2147483647 || BigInt(query.page + 1) * BigInt(20) >= count(data.filteredTotal)} onClick={() => changeQuery(query.filters, query.page + 1)}>Trang sau</Button>
            </Group>
          </Group>}
          {query.page === 0 && data.items.length === 0 && <Text size="xs" color="dimmed">Trang 1</Text>}
        </>}
      </Stack>
    </Paper>
    {notice && <Alert color="blue" role="status" id="combo-review-notice">{notice}</Alert>}
    {savedAudit && <Paper withBorder className={classes.card}><Title order={4} mb="sm">Quyết định đã lưu</Title><ReviewAudit audit={savedAudit}/></Paper>}
    {selected && <Paper withBorder className={classes.card}><Stack spacing="lg">
      <Group position="apart" align="flex-start"><Title order={3} className={classes.wrap}>Chi tiết Combo {selected}</Title><Button variant="default" disabled={sending} onClick={() => { detailGeneration.current++; setSelected(null); setDetail(null); }}>Đóng chi tiết</Button></Group>
      {detailLoading && <Stack role="status"><Text color="dimmed">Đang tải chi tiết…</Text><Skeleton height={100}/></Stack>}
      {detailError && <Alert color="red" role="alert">{detailError}<Group mt="sm"><Button variant="light" onClick={invalidate}>Thử lại chi tiết</Button></Group></Alert>}
      {detail && <>
        <div><Group spacing="sm" mb="sm">
          <Badge color={statusColor(detail.data.comboStatus)} variant="light" className={classes.tag}>{statusLabel(detail.data.comboStatus)}</Badge>
          <Badge variant="outline" className={classes.tag}>{batchLabel(detail.data.batchStatus)}</Badge>
          <Badge variant="light" className={classes.tag}>{kindLabel[detail.data.comboKind] ?? detail.data.comboKind}</Badge>
        </Group><Text color="dimmed" size="sm" className={classes.wrap}>Run: {detail.data.runId}</Text>
        <Text className={classes.wrap} mt="sm">{detail.data.explanation}</Text></div>
        <SimpleGrid cols={3} breakpoints={[{ maxWidth: 'md', cols: 2 }, { maxWidth: 'xs', cols: 1 }]} spacing="sm">
          <Text className={classes.wrap}>Support: {detail.data.metrics.supportAbs}; Weight: {detail.data.metrics.patternWeight ?? 'Không khả dụng'}</Text>
          <Text className={classes.wrap}>Giá tham chiếu: {detail.data.referenceOriginalPrice} {detail.data.currencyCode}</Text>
          <Text className={classes.wrap}>Loại Combo: {detail.data.comboKind}</Text>
        </SimpleGrid>
        <div><Title order={4} mb="sm">Sản phẩm theo thứ tự</Title><Stack spacing="sm">
          {detail.itemsets.map(set => <fieldset className={classes.itemset} key={set.itemsetOrder} aria-label={'Itemset ' + set.itemsetOrder}><legend>Itemset {set.itemsetOrder}</legend>
            {set.items.map(i => <Text className={classes.wrap} key={i.itemOrder}>Vị trí {i.itemOrder}: {i.nameSnapshot ?? i.productId} — {i.skuSnapshot ?? 'Thiếu snapshot'}; Số lượng {i.quantity}; Neo: {i.anchorEligible ? 'Có' : 'Không'}</Text>)}
          </fieldset>)}
        </Stack></div>
        <div><Title order={4} mb="sm">Pattern nguồn</Title><Stack spacing="sm">
          {detail.sources.map(s => <Paper withBorder p="md" radius="sm" key={s.patternId} sx={{ minWidth: 0 }}>
            <Badge variant={s.primary ? 'filled' : 'light'} mb="xs">{s.primary ? 'Nguồn chính' : 'Nguồn phụ'}</Badge>
            <Text className={classes.code}>{s.primary ? 'Nguồn chính' : 'Nguồn phụ'}: {s.patternId}; Run {s.sourceRunId}; {s.canonicalPattern}; Support {s.metrics.supportAbs}</Text>
          </Paper>)}
        </Stack></div>
        <div><Title order={4} mb="sm">Lịch sử review</Title><ReviewAudit audit={detail.data.review}/></div>
        <Paper withBorder p="md" radius="sm"><Stack spacing="sm">
          <Title order={4}>Review Combo</Title>
          {detail.assessment.blockingIssues.map(i => <Text color="red" size="sm" className={classes.wrap} key={i.code}>Chặn: {blockers[i.code] ?? i.code}</Text>)}
          {detail.assessment.warnings.map(i => <Text size="sm" className={classes.wrap} key={i.code}>Cảnh báo: {blockers[i.code] ?? i.code}</Text>)}
          {(!canReview('APPROVE') && !canReview('REJECT')) && <Text size="sm" color="dimmed">Combo này hiện chỉ có thể xem. Hãy kiểm tra các lý do bên trên.</Text>}
          <Group><Button color="teal" disabled={!canReview('APPROVE')} onClick={() => setDecision('APPROVE')}>Duyệt</Button><Button variant="light" color="red" disabled={!canReview('REJECT')} onClick={() => setDecision('REJECT')}>Từ chối</Button></Group>
          {decision && <form onSubmit={e => { e.preventDefault(); void submit(); }}><Stack spacing="sm">
            <Text weight={500} className={classes.wrap}>Xác nhận {decision === 'APPROVE' ? 'duyệt' : 'từ chối'} cho {detail.data.name}</Text>
            <Textarea label="Ghi chú review" minRows={3} autosize value={note} disabled={sending} aria-describedby={notice ? 'combo-review-notice' : undefined}
              error={notice === 'Cần ghi chú từ chối.' || notice === 'Ghi chú không hợp lệ hoặc vượt giới hạn.'} onChange={e => setNote(e.target.value)}/>
            <Group><Button type="submit" color={decision === 'APPROVE' ? 'teal' : 'red'} disabled={sending || !canReview(decision)}>{sending ? 'Đang gửi…' : 'Xác nhận quyết định'}</Button></Group>
          </Stack></form>}
        </Stack></Paper>
      </>}
    </Stack></Paper>}
  </Stack>;
}
