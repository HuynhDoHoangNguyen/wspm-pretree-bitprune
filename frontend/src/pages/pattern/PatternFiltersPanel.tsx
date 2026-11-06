import React, { useEffect, useRef, useState } from 'react';
import { ActionIcon, Alert, Badge, Button, Checkbox, Group, Paper, SimpleGrid, Stack, Text, TextInput, Title } from '@mantine/core';
import { Search, X } from 'tabler-icons-react';
import { useMiningAdminStyles } from '../admin/data-mining/MiningAdminUi';
import { DictionaryOption, DictionaryPage, PatternClient, PatternClientError, PatternFilters, WeightCapability } from './PatternClient';

export function validateFilters(ids: string[], bounds: Record<string, string>, enabled: boolean): PatternFilters {
  if (ids.length > 100) throw new Error('Chỉ chọn tối đa 100 sản phẩm.');
  if (ids.some(id => !/^[0-9]+$/.test(id) || BigInt(id) < BigInt(1) || BigInt(id) > BigInt(2147483647)))
    throw new Error('Item đã chọn nằm ngoài miền backend hỗ trợ.');
  const result: PatternFilters = {};
  if (ids.length) result.itemIds = Array.from(new Set(ids)).sort((a, b) => Number(a) - Number(b));
  for (const key of ['supportMin', 'supportMax'] as const) {
    const raw = bounds[key];
    if (raw === '') continue;
    if (!/^[0-9]{1,19}$/.test(raw) || BigInt(raw) > BigInt('9223372036854775807'))
      throw new Error('Support phải là số sequence nguyên từ 0 đến 9223372036854775807.');
    result[key] = BigInt(raw).toString();
  }
  if (result.supportMin !== undefined && result.supportMax !== undefined && BigInt(result.supportMin) > BigInt(result.supportMax))
    throw new Error('Support tối thiểu không được lớn hơn tối đa.');
  if (enabled) {
    for (const key of ['weightMin', 'weightMax'] as const) {
      const raw = bounds[key];
      if (raw === '') continue;
      if (raw.length > 64 || !/^-?[0-9]+(?:\.[0-9]+)?$/.test(raw) || !Number.isFinite(Number(raw)))
        throw new Error('Trọng số phải là số thập phân hữu hạn, không dùng số mũ.');
      result[key] = raw;
    }
    if (result.weightMin !== undefined && result.weightMax !== undefined && Number(result.weightMin) > Number(result.weightMax))
      throw new Error('Trọng số tối thiểu không được lớn hơn tối đa.');
  }
  return result;
}
const emptyBounds = { supportMin: '', supportMax: '', weightMin: '', weightMax: '' };
export default function PatternFiltersPanel({ client, runId, capability, serverError, onApply }: {
  client: PatternClient; runId: string; capability: WeightCapability; serverError: string;
  onApply: (filters: PatternFilters) => void;
}) {
  const { classes } = useMiningAdminStyles();
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<DictionaryPage | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [validation, setValidation] = useState('');
  const [selected, setSelected] = useState<DictionaryOption[]>([]);
  const [bounds, setBounds] = useState(emptyBounds);
  const [retry, setRetry] = useState(0);
  const generation = useRef(0);
  useEffect(() => {
    if (!capability.enabled) setBounds(previous => ({ ...previous, weightMin: '', weightMax: '' }));
  }, [capability.enabled]);
  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    const token = ++generation.current;
    setData(null); setError('');
    if (Array.from(query.trim()).length > 100) { setError('Từ khóa tối đa 100 ký tự.'); setLoading(false); return; }
    setLoading(true);
    client.dictionary(runId, query, page, 20, { signal: controller.signal }).then(value => {
      if (!active || controller.signal.aborted || generation.current !== token) return;
      if (value.runId !== runId || value.page !== page || value.size !== 20) throw new Error('Mismatched dictionary');
      if (value.matchedTotal !== null && !/^\d+$/.test(value.matchedTotal)) throw new Error('Invalid dictionary count');
      setData(value);
    }).catch(reason => {
      if (!active || controller.signal.aborted || generation.current !== token) return;
      setError(reason instanceof PatternClientError && reason.status === 403 ? 'Không có quyền đọc dictionary.'
        : reason instanceof PatternClientError && reason.status === 401 ? 'Vui lòng đăng nhập lại.' : 'Không tải được dictionary.');
    }).finally(() => { if (active && !controller.signal.aborted && generation.current === token) setLoading(false); });
    return () => { active = false; controller.abort(); };
  }, [client, runId, query, page, retry]);
  function apply() {
    try { const value = validateFilters(selected.map(item => String(item.itemId)), bounds, capability.enabled);
      setValidation(''); onApply(value);
    } catch (reason) { setValidation(reason instanceof Error ? reason.message : 'Bộ lọc không hợp lệ.'); }
  }
  return <Paper withBorder className={classes.card}><Stack spacing="md">
    <div><Title order={3}>Bộ lọc Pattern</Title>
      <Text size="sm" color="dimmed" mt={4}>Chứa tất cả sản phẩm đã chọn, kể cả khi nằm ở các itemset khác nhau.</Text></div>
    <SimpleGrid cols={2} spacing="lg" breakpoints={[{ maxWidth: 'md', cols: 1 }]}>
      <Stack spacing="sm" sx={{ minWidth: 0 }}>
        <TextInput size="sm" icon={<Search size={16}/>} label="Tìm sản phẩm theo tên hoặc SKU snapshot" value={query} onChange={e => {
          generation.current++; setData(null); setQuery(e.currentTarget.value); setPage(0);
        }}/>
        {loading && <Text role="status">Đang tìm dictionary…</Text>}
        {error && <Alert color="red" role="alert">{error}<Button variant="light" mt="sm" onClick={() => setRetry(x => x + 1)}>Tải lại dictionary</Button></Alert>}
        {data?.resultState === 'READY' && <>
          <Text size="sm" color="dimmed">Dictionary khớp: {data.matchedTotal ?? 'Chưa khả dụng'}</Text>
          <Stack spacing="sm" className={classes.dictionary}>
            {data.items.length === 0 && <Text size="sm" color="dimmed">Không có sản phẩm trong dictionary khớp từ khóa.</Text>}
            {data.items.map(item => <Checkbox key={item.itemId} label={`${item.nameSnapshot} — ${item.skuSnapshot} (item ${item.itemId})`}
              checked={selected.some(s => s.itemId === item.itemId)} onChange={e => {
                if (e.currentTarget.checked) setSelected(previous => previous.some(s => s.itemId === item.itemId) ? previous : [...previous, item]);
                else setSelected(previous => previous.filter(s => s.itemId !== item.itemId));
              }}/>)}</Stack>
          {(page > 0 || data.items.length > 0) && <Group position="apart" spacing="xs"><Button variant="default" compact disabled={page === 0} onClick={() => { generation.current++; setPage(page - 1); }}>Dictionary trước</Button>
            <Text size="xs" color="dimmed">Trang dictionary {page + 1}</Text>
            <Button variant="default" compact disabled={data.matchedTotal === null || BigInt(data.matchedTotal) <= BigInt(page + 1) * BigInt(20) || page >= 2147483647}
              onClick={() => { generation.current++; setPage(page + 1); }}>Dictionary sau</Button></Group>}
        </>}
        {data && data.resultState !== 'READY' && <Text>Dictionary chưa sẵn sàng: {data.resultState}</Text>}
        <Text size="sm" weight={500}>Đã chọn: {selected.length}</Text>
        <Group spacing={8}>{selected.map(item => <Badge key={item.itemId} variant="light" className={classes.tag}
          rightSection={<ActionIcon size="xs" aria-label={`Bỏ item ${item.itemId}`} onClick={() => setSelected(previous => previous.filter(s => s.itemId !== item.itemId))}><X size={12}/></ActionIcon>}>
          {item.nameSnapshot} ({item.skuSnapshot})</Badge>)}</Group>
      </Stack>
      <Stack spacing="sm" sx={{ minWidth: 0 }}>
        <Text size="sm" weight={600}>Khoảng giá trị</Text>
        <SimpleGrid cols={2} spacing="sm" breakpoints={[{ maxWidth: 'xs', cols: 1 }]}>{(['supportMin', 'supportMax', 'weightMin', 'weightMax'] as const).map(key => <TextInput key={key} size="sm"
          label={{ supportMin: 'Support tối thiểu (sequence)', supportMax: 'Support tối đa (sequence)', weightMin: 'Trọng số mẫu tối thiểu', weightMax: 'Trọng số mẫu tối đa' }[key]}
          value={bounds[key]} disabled={key.startsWith('weight') && !capability.enabled}
          aria-describedby="pattern-bounds-hint" onChange={e => { const value = e.currentTarget.value; setBounds(previous => ({ ...previous, [key]: value })); }}/>)}</SimpleGrid>
        <Text id="pattern-bounds-hint" size="xs" color="dimmed">Các cận bao gồm giá trị đúng biên; để trống là không giới hạn.</Text>
        {!capability.enabled && <Text size="sm" color="dimmed">Lọc trọng số chưa khả dụng: chưa có trọng số được xác nhận cho run này.</Text>}
      </Stack></SimpleGrid>
    {(validation || serverError) && <Alert color="red" role="alert">{validation || serverError}</Alert>}
    <Group className={classes.footer}><Button onClick={apply}>Áp dụng</Button><Button variant="default" onClick={() => {
      setSelected([]); setBounds(emptyBounds); setValidation(''); onApply({});
    }}>Bỏ lọc</Button></Group>
  </Stack></Paper>;
}
