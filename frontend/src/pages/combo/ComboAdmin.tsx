import React, { useEffect, useRef, useState } from 'react';
import { Alert, Button, Group, Paper, Stack, Table, Text, Title } from '@mantine/core';
import { Audit, ComboClient, ComboClientError, ComboDetail, ComboFilters, ComboPage, Decision, unavailableComboClient } from './ComboClient';

const statuses: Record<string, string> = { DRAFT: 'Bản nháp', APPROVED: 'Đã duyệt nội bộ', REJECTED: 'Đã từ chối', PUBLISHED: 'Đã công bố', ARCHIVED: 'Đã lưu trữ' };
const kinds = ['SAME_ORDER_BUNDLE', 'SEQUENTIAL_CROSS_SELL', 'MIXED_SEQUENCE'];
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
  return <div><Text>Người review: {audit.reviewerDisplayName ?? audit.reviewedBy ?? 'Chưa có'}{audit.reviewerDisplayName && audit.reviewedBy ? ` (${audit.reviewedBy})` : ''}</Text>
    <Text>Thời điểm review (UTC): {audit.reviewedAt ?? 'Chưa có'}</Text><Text style={{ whiteSpace: 'pre-wrap' }}>Ghi chú: {audit.reviewNote ?? 'Không có'}</Text></div>;
}
export default function ComboAdmin({ client = unavailableComboClient }: { client?: ComboClient }) {
  const [draft, setDraft] = useState<ComboFilters>({});
  const [query, setQuery] = useState({ filters: {} as ComboFilters, page: 0 });
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
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const invalidate = () => { listGeneration.current++; detailGeneration.current++; setRevision(v => v + 1); };
  const changeQuery = (filters: ComboFilters, page: number) => {
    listGeneration.current++; detailGeneration.current++; setSelected(null); setDetail(null); setDecision(null);
    setNote(''); setQuery({ filters, page });
  };
  useEffect(() => {
    const generation = ++listGeneration.current; let active = true;
    setLoading(true); setData(null); setError('');
    client.list(query.filters, query.page, 20).then(result => {
      if (!active || generation !== listGeneration.current) return;
      const total = count(result.filteredTotal); count(result.scopeTotal);
      const last = total === BigInt(0) ? BigInt(0) : (total - BigInt(1)) / BigInt(20);
      if (BigInt(query.page) > last) { setQuery(q => ({ ...q, page: Number(last) })); return; }
      if (query.page > 0 && result.items.length === 0) { setQuery(q => ({ ...q, page: 0 })); return; }
      setData(result);
    }).catch(e => { if (active && generation === listGeneration.current) setError(failure(e)); })
      .finally(() => { if (active && generation === listGeneration.current) setLoading(false); });
    return () => { active = false; };
  }, [client, query, revision]);
  useEffect(() => {
    const generation = ++detailGeneration.current; let active = true;
    setDetail(null); setDetailError(''); setDecision(null); setNote('');
    if (!selected) { setDetailLoading(false); return; }
    setDetailLoading(true);
    client.detail(selected).then(result => {
      if (!active || generation !== detailGeneration.current) return;
      if (result.data.comboId !== selected) throw new Error('DETAIL_CONTEXT_MISMATCH');
      setDetail(result); setReconciling(false);
    }).catch(e => { if (active && generation === detailGeneration.current) setDetailError(failure(e)); })
      .finally(() => { if (active && generation === detailGeneration.current) setDetailLoading(false); });
    return () => { active = false; };
  }, [client, selected, revision]);
  const canReview = (action: Decision) => !!(detail && detail.data.comboStatus === 'DRAFT' && client.reviewReady
    && client.notePolicy?.confirmed && detail.reviewToken?.trim() && detail.assessment.allowedActions?.includes(action)
    && detail.assessment.blockingIssues.length === 0 && !sending && !reconciling && !detailLoading);
  const submit = async () => {
    if (!decision || !detail || !canReview(decision)) return;
    const policy = client.notePolicy, token = detail.reviewToken;
    if (!policy?.confirmed || !token) return;
    const normalized = note.trim() || null;
    if (decision === 'REJECT' && policy.rejectRequired && !normalized) { setNotice('Cần ghi chú từ chối.'); return; }
    if (normalized && (Array.from(normalized).length > policy.maxCodePoints || Array.from(normalized).some(invalidNoteCharacter))) {
      setNotice('Ghi chú không hợp lệ hoặc vượt giới hạn.'); return;
    }
    const generation = ++sendGeneration.current;
    setSending(true); setNotice(''); setSavedAudit(null);
    try {
      const result = await client.review(detail.data.comboId, { decision, reviewNote: normalized, expectedReviewToken: token });
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
      setNotice(failure(e) + ' Đang đọc lại trước khi thử lại.'); setReconciling(true); invalidate();
    } finally {
      if (mounted.current && generation === sendGeneration.current) { setSending(false); setDecision(null); }
    }
  };
  return <Stack><Title order={2}>Quản lý Combo</Title>
    <Paper p="md" withBorder><form onSubmit={e => {
      e.preventDefault(); if (draft.batchId && (draft.batchId.trim() === '' || Array.from(draft.batchId).length > 40)) { setNotice('Batch ID không hợp lệ.'); return; }
      changeQuery(draft, 0);
    }}><Group align="end">
        <label>Batch ID<input aria-label="Batch ID" value={draft.batchId ?? ''} onChange={e => { const value = e.target.value; setDraft(d => ({ ...d, batchId: value || undefined })); }}/></label>
        <label>Trạng thái Combo<select aria-label="Trạng thái Combo" value={draft.status ?? ''} onChange={e => { const value = e.target.value; setDraft(d => ({ ...d, status: value || undefined })); }}>
          <option value="">Tất cả</option>{Object.keys(statuses).map(s => <option key={s} value={s}>{statuses[s]}</option>)}</select></label>
        <label>Loại Combo<select aria-label="Loại Combo" value={draft.comboKind ?? ''} onChange={e => { const value = e.target.value; setDraft(d => ({ ...d, comboKind: value || undefined })); }}>
          <option value="">Tất cả</option>{kinds.map(k => <option key={k}>{k}</option>)}</select></label>
        <Button type="submit">Áp dụng bộ lọc</Button><Button onClick={() => { setDraft({}); changeQuery({}, 0); }}>Bỏ lọc</Button>
      </Group></form></Paper>
    {notice && <Alert role="status">{notice}</Alert>}{savedAudit && <ReviewAudit audit={savedAudit}/>}
    {loading && <Text role="status">Đang tải danh sách…</Text>}
    {error && <Alert role="alert">{error}<Button onClick={invalidate}>Thử lại danh sách</Button></Alert>}
    {data && <><Text>Trong phạm vi: {data.scopeTotal}; Khớp bộ lọc: {data.filteredTotal}</Text>
      {data.items.length === 0 ? <Text>{count(data.scopeTotal) === BigInt(0) ? 'Chưa có Combo trong phạm vi đã chọn.' : 'Không có Combo khớp bộ lọc.'}</Text> :
        <Table><thead><tr><th>Combo</th><th>Trạng thái Combo</th><th>Batch</th><th>Audit</th></tr></thead><tbody>{data.items.map(s => <tr key={s.data.comboId}>
          <td><Button variant="subtle" onClick={() => { if (selected === s.data.comboId) { invalidate(); return; } detailGeneration.current++; setSelected(s.data.comboId); setDetail(null); }}>{s.data.name} ({s.data.comboId})</Button></td>
          <td>{statusLabel(s.data.comboStatus)}</td><td>{s.data.batchId}: {batchLabel(s.data.batchStatus)}</td><td><ReviewAudit audit={s.data.review}/></td>
        </tr>)}</tbody></Table>}
      <Group><Button disabled={query.page === 0} onClick={() => changeQuery(query.filters, query.page - 1)}>Trang trước</Button><Text>Trang {query.page + 1}</Text>
        <Button disabled={query.page >= 2147483647 || BigInt(query.page + 1) * BigInt(20) >= count(data.filteredTotal)} onClick={() => changeQuery(query.filters, query.page + 1)}>Trang sau</Button></Group></>}
    {selected && <Paper withBorder p="md"><Group><Title order={3}>Chi tiết Combo {selected}</Title><Button onClick={() => { detailGeneration.current++; setSelected(null); setDetail(null); }}>Đóng chi tiết</Button></Group>
      {detailLoading && <Text>Đang tải chi tiết…</Text>}{detailError && <Alert role="alert">{detailError}<Button onClick={invalidate}>Thử lại chi tiết</Button></Alert>}
      {detail && <Stack><Text>{statusLabel(detail.data.comboStatus)}; {batchLabel(detail.data.batchStatus)}; Run: {detail.data.runId}</Text>
        <ReviewAudit audit={detail.data.review}/><Text>Loại Combo: {detail.data.comboKind}</Text><Text>Support: {detail.data.metrics.supportAbs}; Weight: {detail.data.metrics.patternWeight ?? 'Không khả dụng'}</Text>
        {detail.itemsets.map(set => <fieldset key={set.itemsetOrder} aria-label={`Itemset ${set.itemsetOrder}`}><legend>Itemset {set.itemsetOrder}</legend>
          {set.items.map(i => <Text key={i.itemOrder}>Vị trí {i.itemOrder}: {i.nameSnapshot ?? i.productId} — {i.skuSnapshot ?? 'Thiếu snapshot'}; Số lượng {i.quantity}; Neo: {i.anchorEligible ? 'Có' : 'Không'}</Text>)}</fieldset>)}
        <Title order={4}>Pattern nguồn</Title>{detail.sources.map(s => <Text key={s.patternId}>{s.primary ? 'Nguồn chính' : 'Nguồn phụ'}: {s.patternId}; Run {s.sourceRunId}; {s.canonicalPattern}; Support {s.metrics.supportAbs}</Text>)}
        {detail.assessment.blockingIssues.map(i => <Text color="red" key={i.code}>Chặn: {i.code}</Text>)}{detail.assessment.warnings.map(i => <Text key={i.code}>Cảnh báo: {i.code}</Text>)}
        {(!canReview('APPROVE') && !canReview('REJECT')) && <Text>Chỉ đọc: cần API review, policy, allowedActions và token được server xác minh.</Text>}
        <Group><Button disabled={!canReview('APPROVE')} onClick={() => setDecision('APPROVE')}>Duyệt</Button><Button disabled={!canReview('REJECT')} onClick={() => setDecision('REJECT')}>Từ chối</Button></Group>
        {decision && <form onSubmit={e => { e.preventDefault(); void submit(); }}><Text>Xác nhận {decision} cho {detail.data.comboId}</Text>
          <label>Ghi chú review<textarea aria-label="Ghi chú review" value={note} disabled={sending} onChange={e => setNote(e.target.value)}/></label>
          <Button type="submit" disabled={sending || !canReview(decision)}>{sending ? 'Đang gửi…' : 'Xác nhận quyết định'}</Button></form>}
      </Stack>}
    </Paper>}
  </Stack>;
}
