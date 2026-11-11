import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Group, MultiSelect, NativeSelect, NumberInput, Paper, Stack, Text, TextInput, Title } from '@mantine/core';
import { Link } from 'react-router-dom';
import { useSharedAuth } from 'auth/session';
import { comboGenerationApi, GenerationPreview, GenerationOutcome } from 'api/admin/data-mining/combo-generation';
import { useWorkspaceRead } from '../admin/data-mining/useWorkspaceRead';

export default function NativeGenerationPanel({ runId }: { runId: string }) {
  const issueText = (code: string) => ({
    GENERATION_RESOURCE_LIMIT_EXCEEDED: 'Đề xuất vượt giới hạn lưu an toàn. Không có Combo nào được lưu; cần thu hẹp dữ liệu nguồn hoặc dùng run có ít nhóm hơn.',
    PAYLOAD_OR_EVIDENCE_INVALID: 'Không thể xác minh gói dữ liệu lưu. Chưa xác nhận lưu thành công; cần kiểm tra backend.',
    INVALID_ANCHOR_MEMBERSHIP: 'Sản phẩm đã chọn không thuộc danh sách neo hợp lệ.',
    ACCESSORY_REQUIRED: 'Thiếu phụ kiện hợp lệ', CATEGORY_NOT_ALLOWED: 'Danh mục ngoài phạm vi',
    PRODUCT_COUNT_OUT_OF_RANGE: 'Số sản phẩm nằm ngoài khoảng đã chọn', REPEATED_PRODUCT: 'Sản phẩm lặp lại',
    LAPTOP_COUNT_INVALID: 'Không có đúng một Laptop',
    REQUIRED_PRODUCTS_MISSING: 'Không chứa đủ sản phẩm bắt buộc',
    REQUIRED_CATEGORIES_MISSING: 'Không chứa đủ danh mục bắt buộc',
  } as Record<string, string>)[code] || code;
  const auth = useSharedAuth();
  const options = useWorkspaceRead('generation-category-options:' + runId, signal => comboGenerationApi.categoryOptions(runId, signal));
  const [selected, setSelected] = useState<string[]>([]);
  const [quantities, setQuantities] = useState<Record<string, string>>({});
  const [minimum, setMinimum] = useState('2'), [maximum, setMaximum] = useState('4');
  const [operation, setOperation] = useState<string | null>(null);
  const session = JSON.stringify([auth.status, auth.accessToken, auth.user?.userId, auth.user?.roles, auth.forbidden]);
  const [anchor, setAnchor] = useState(''); const [k, setK] = useState<number | undefined>(5);
  const [recommendationK, setRecommendationK] = useState<number | undefined>(5);
  const [draftLimit, setDraftLimit] = useState<number | undefined>(20);
  const [preview, setPreview] = useState<GenerationPreview | null>(null);
  const [outcome, setOutcome] = useState<GenerationOutcome | null>(null);
  const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const id = useRef(crypto.randomUUID()); const pending = useRef(false); const active = useRef<AbortController | null>(null);
  const epoch = useRef(0); const [unknown, setUnknown] = useState(false);
  const abortActive = useCallback(() => { epoch.current++; active.current?.abort(); }, []);
  useEffect(() => { abortActive(); pending.current = false; setBusy(false);
    setPreview(null); setOutcome(null); setUnknown(false); setError(''); id.current = crypto.randomUUID();
    setSelected([]); setQuantities({}); setMinimum('2'); setMaximum('4'); setAnchor(''); setK(5); setRecommendationK(5); setDraftLimit(20); setOperation(null);
    return abortActive;
  }, [runId, session, abortActive]);
  const invalidate = () => { setPreview(p => p ? { ...p, contextToken: null } : null); setOutcome(null); setError(''); };
  const configure = () => { setPreview(null); setAnchor(''); setOutcome(null); setError(''); };
  const integer = (value: string, max: number) => /^[1-9][0-9]*$/.test(value) && Number.isSafeInteger(Number(value)) && Number(value) <= max;
  const limits = options.value;
  const selectionError = !limits ? 'Đang chờ danh mục của run.' : !selected.length ? 'Chọn ít nhất một danh mục bắt buộc.'
    : !integer(minimum, limits.maxDistinctProducts) || Number(minimum) < 2 || !integer(maximum, limits.maxDistinctProducts) || Number(minimum) > Number(maximum)
      ? `Khoảng số sản phẩm phải từ 2 đến ${limits.maxDistinctProducts}, tối thiểu không vượt tối đa.`
      : selected.length > Number(maximum) ? `Đã chọn ${selected.length} danh mục bắt buộc nhưng tối đa chỉ ${maximum} sản phẩm. Hãy bỏ bớt danh mục hoặc tăng số sản phẩm tối đa.`
        : selected.some(item => !integer(quantities[item] || '', limits.maxQuantity)) ? `Số lượng phải là số nguyên từ 1 đến ${limits.maxQuantity}.` : '';
  async function invoke(kind: 'preview' | 'submit' | 'reconcile') {
    if (pending.current || auth.status !== 'authenticated' || auth.forbidden) return;
    if (kind === 'preview' && selectionError) { setError(selectionError); return; }
    if (kind === 'preview' && (typeof k !== 'number' || !Number.isInteger(k) || k < 1 || k > 20)) { setError('Số đề xuất xem trước phải là số nguyên từ 1 đến 20.'); return; }
    if (kind === 'preview' && (typeof recommendationK !== 'number' || !Number.isInteger(recommendationK) || recommendationK < 1 || recommendationK > 20)) { setError('Top-K gợi ý cho khách phải là số nguyên từ 1 đến 20.'); return; }
    if (kind === 'preview' && (typeof draftLimit !== 'number' || !Number.isInteger(draftLimit) || draftLimit < 1 || draftLimit > 1000)) { setError('Số combo nháp muốn lưu phải là số nguyên từ 1 đến 1000.'); return; }
    const contextToken=preview?.contextToken;
    if (kind !== 'preview' && !contextToken) return;
    const generation = epoch.current; const controller = new AbortController(); active.current = controller;
    pending.current = true; setBusy(true); setOperation(kind); setError('');
    try {
      if (kind === 'preview') {
        const p = await comboGenerationApi.previewCategories(runId, anchor || null, Number(k), id.current, controller.signal, {
          requiredCategories: selected.map(item => ({ categoryId: item, quantity: Number(quantities[item]) })),
          minDistinctProducts: Number(minimum), maxDistinctProducts: Number(maximum),
        }, Number(recommendationK), Number(draftLimit));
        if (generation === epoch.current) {
          setPreview(p); setOutcome(null);
          if (anchor && !p.anchors.some(a => a.productId === anchor)) {
            setPreview({ ...p, contextToken: null });
            setAnchor('');
            if (p.groups > 0) setError('Sản phẩm neo trước đó không còn trong các Combo phù hợp. Đã bỏ lựa chọn neo; bấm Xem trước Combo để cập nhật.');
          }
        }
      } else if (contextToken) {
        const result = await comboGenerationApi[kind](contextToken, controller.signal);
        if (generation === epoch.current) {
          setOutcome(result); setUnknown(result.status === 'UNKNOWN');
          if (!['UNKNOWN', 'INSERTED', 'NO_OP'].includes(result.status)) setPreview(p => p ? { ...p, contextToken: null } : null);
        }
      }
    } catch {
      if (generation !== epoch.current) return;
      if (kind !== 'preview') { setUnknown(true); setError('Chưa xác nhận kết quả ghi. Hãy đối soát, không tự gửi lại.'); }
      else { setPreview(null); setAnchor(''); setError('Không thể chuẩn bị Combo. Kiểm tra quyền, publication và catalog rồi thử lại.'); }
    } finally { if (generation === epoch.current) { pending.current = false; setBusy(false); setOperation(null); } }
  }
  const done = !!outcome && ['INSERTED', 'NO_OP'].includes(outcome.status);
  return <section aria-label="Sinh Combo DRAFT"><Stack>
    <Title order={4}>Xem đề xuất và lưu Combo</Title>
    <Text>Chọn danh mục bắt buộc; mỗi danh mục phải có sản phẩm trong cùng Pattern nguồn. Không chọn mẫu sản phẩm cụ thể ở bước này.</Text>
    {options.loading && <Text role="status">Đang tải danh mục của run…</Text>}
    {options.error && <Alert color="red">Không tải được danh mục.<Button onClick={options.refresh}>Tải lại danh mục</Button></Alert>}
    <MultiSelect label="Danh mục bắt buộc trong Combo" placeholder="Tìm danh mục sản phẩm" searchable clearable
      data={(limits?.categories || []).map(c => ({ value: c.categoryId, label: `${c.name} (${c.productCount} sản phẩm)` }))}
      value={selected} disabled={!limits || busy || unknown || done} onChange={values => {
        setSelected(values); setQuantities(previous => Object.fromEntries(values.map(item => [item, previous[item] || '1']))); configure();
      }}/>
    {selected.map(item => {
      const category = limits?.categories.find(c => c.categoryId === item);
      return <Group key={item} align="flex-end"><TextInput label={'Số lượng mỗi sản phẩm · ' + (category?.name || item)} inputMode="numeric" value={quantities[item] || ''}
        disabled={busy || unknown || done} error={limits && !integer(quantities[item] || '', limits.maxQuantity) ? `Số nguyên 1–${limits.maxQuantity}` : undefined}
        onChange={e => { const value = e.currentTarget.value; setQuantities(q => ({ ...q, [item]: value })); configure(); }}/>
      <Button variant="subtle" disabled={busy || unknown || done} onClick={() => {
        setSelected(s => s.filter(i => i !== item)); setQuantities(q => { const next = { ...q }; delete next[item]; return next; }); configure();
      }}>Bỏ {category?.name || item}</Button></Group>;
    })}
    <Group grow><TextInput label="Số sản phẩm tối thiểu trong Combo" inputMode="numeric" value={minimum} disabled={busy || unknown || done}
      onChange={e => { setMinimum(e.currentTarget.value); configure(); }}/>
    <TextInput label="Số sản phẩm tối đa trong Combo" inputMode="numeric" value={maximum} disabled={busy || unknown || done}
      onChange={e => { setMaximum(e.currentTarget.value); configure(); }}/></Group>
    <Text size="sm">Khoảng trên là số sản phẩm phân biệt, không phải số danh mục hay tổng số lượng. Quantity của danh mục áp dụng cho mỗi sản phẩm thuộc danh mục đó; sản phẩm ngoài các danh mục đã chọn có số lượng 1.
      {limits && ` Giới hạn: ${limits.maxDistinctProducts} sản phẩm/Combo; ${limits.maxBatchItems} dòng sản phẩm/batch; số lượng tối đa ${limits.maxQuantity}.`}</Text>
    {limits && selectionError && <Text color="red">{selectionError}</Text>}
    <Text>Lưu tối đa số combo nháp đã nhập trên toàn batch: support giảm dần, bằng support thì weight giảm dần. Sản phẩm neo và số đề xuất xem trước chỉ lọc phần xem trước.</Text>
    <NumberInput label="Số combo nháp muốn lưu" value={draftLimit} min={1} max={1000} precision={0}
      disabled={busy || unknown || done} onChange={n => { setDraftLimit(n); id.current = crypto.randomUUID(); invalidate(); }}/>
    {preview && <Text>Có {preview.groups} Combo hợp lệ; sẽ lưu {preview.plannedGroups} Combo nháp theo thứ hạng toàn batch.</Text>}
    {!preview && <Text>Bấm “Xem trước Combo” để tìm đề xuất. Thao tác này chưa lưu dữ liệu.</Text>}
    <NativeSelect label="Sản phẩm neo (chỉ lọc xem trước)" value={anchor} disabled={busy || unknown || done || !preview?.anchors.length}
      data={[{ value: '', label: !preview ? 'Cập nhật xem trước để tải sản phẩm neo' : !preview.anchors.length ? 'Chưa có sản phẩm neo phù hợp' : 'Tất cả đề xuất' }, ...(preview?.anchors || []).map(a => ({ value: a.productId, label: a.name + ' (' + a.productId + ')' }))]}
      onChange={e => { setAnchor(e.target.value); invalidate(); }}/>
    {!preview && <Text size="sm" color="dimmed">Sau khi chọn hoặc thay đổi danh mục, số lượng hay khoảng sản phẩm, bấm Xem trước Combo để tải lại danh sách neo theo cấu hình mới. Danh sách cũ không được dùng lại.</Text>}
    {preview && !preview.anchors.length && <Alert color="yellow">{preview.status === 'EMPTY'
      ? 'Không có sản phẩm neo vì chưa có Combo hợp lệ theo cấu hình này. Kiểm tra danh mục bắt buộc và khoảng số sản phẩm; không phải mọi danh mục trong run đều cùng xuất hiện trong một Pattern.'
      : 'Chưa có danh sách sản phẩm neo được xác minh. Xem thông báo kết quả và thử cập nhật xem trước.'}</Alert>}
    <NumberInput label="Số đề xuất xem trước" value={k} min={1} max={20} disabled={busy || unknown || done} onChange={n => { setK(n); invalidate(); }}/>
    <NumberInput label="Top-K gợi ý cho khách" description="Áp dụng riêng cho gợi ý mua cùng và gợi ý cá nhân sau khi lọc điều kiện."
      value={recommendationK} min={1} max={20} precision={0} disabled={busy || unknown || done}
      onChange={n => { setRecommendationK(n); id.current = crypto.randomUUID(); invalidate(); }}/>
    <Text size="sm" color="dimmed">Mặc định 5, tối đa 20 gợi ý mỗi nhóm. Giới hạn này độc lập với số combo nháp muốn lưu. Muốn đổi Top-K sau khi lưu hoặc công bố, hãy tạo batch mới.</Text>
    {preview && <Text size="sm">Chọn bất kỳ sản phẩm nào có trong Combo để lọc đề xuất xem trước, kể cả sản phẩm ở itemset sau. Lựa chọn này không thay đổi thứ tự mua hoặc các Combo được lưu.</Text>}
    <Group><Button loading={operation === 'preview'} onClick={() => void invoke('preview')} disabled={busy || unknown || done || !!selectionError}>Xem trước Combo</Button>
      {preview && <Button loading={operation === 'submit'} onClick={() => void invoke('submit')} disabled={busy || unknown || done || !preview.contextToken}>Lưu {preview.plannedGroups} Combo DRAFT</Button>}
      {unknown && <Button loading={operation === 'reconcile'} onClick={() => void invoke('reconcile')} disabled={busy}>Kiểm tra kết quả lưu</Button>}</Group>
    {operation === 'submit' && <Text role="status">Đang lưu {preview?.plannedGroups} Combo và xác minh dữ liệu. Không gửi lại yêu cầu.</Text>}
    {preview && preview.status === 'READY' && !preview.contextToken && !preview.issues.length && <Text>Hãy cập nhật xem trước sau khi đổi lựa chọn. Chưa thể lưu đề xuất cũ.</Text>}
    {preview && <Text>Đã xét {preview.examined} Pattern; {preview.accepted} hợp lệ; {preview.rejected} bị loại; {preview.groups} nhóm Combo.
      {` Đang xem ${preview.proposals?.length || 0} đề xuất; sẽ lưu ${preview.plannedGroups} Combo. Top-K gợi ý cho khách: ${recommendationK ?? '—'}.`}</Text>}
    {preview?.contextToken && preview.proposals?.map(p => <Paper withBorder p="md" key={p.sourcePatternId}>
      <Text weight={600}>Pattern {p.sourcePatternId} · {p.comboKind} · Tổng {p.total} VND</Text><Text component="code">{p.canonicalPattern}</Text>
      {p.items.map(i => <Text key={i.itemsetOrder + ':' + i.itemOrder}>Itemset {i.itemsetOrder}, vị trí {i.itemOrder}: {i.name} · {i.sku} ·
        {i.required ? ' Thuộc danh mục bắt buộc' : ' Bổ sung từ Pattern'} · {i.quantity} × {i.unitPrice} = {i.lineTotal} VND</Text>)}
    </Paper>)}
    {preview && <details><summary>Lý do loại Pattern</summary>{Object.entries(preview.reasonCounts).map(([reason, count]) => <Text size="sm" key={reason}>{issueText(reason)}: {count}</Text>)}</details>}
    {preview?.contextToken && Object.entries(preview.referencePrices).map(([source, price]) => <Text key={source}>Pattern {source}: giá tham chiếu {price} VND · giá bán bằng giá gốc.</Text>)}
    {preview && preview.issues.length > 0 && <Alert color="yellow">{preview.issues.map(issueText).join(' ')}</Alert>}
    {error && <Alert color="yellow" role="alert">{error}</Alert>}
    {outcome && <Alert color={done ? 'teal' : 'yellow'}>{done ? 'Đã lưu và xác minh Combo.' : outcome.issues.map(issueText).join(' ') || 'Chưa xác nhận kết quả lưu.'}
      {done && outcome.batchId && <><Text>Đã lưu {outcome.storedGroups} Combo.</Text><Button component={Link} to={'/admin/combos?batchId=' + encodeURIComponent(outcome.batchId)}>Mở batch đã lưu</Button></>}
    </Alert>}
    {done && <Button variant="subtle" onClick={() => { id.current = crypto.randomUUID(); setPreview(null); setAnchor(''); setOutcome(null); setError(''); }}>Tạo batch Combo khác từ run này</Button>}
  </Stack></section>;
}
