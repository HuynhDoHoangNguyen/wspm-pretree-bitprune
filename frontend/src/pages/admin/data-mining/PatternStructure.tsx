import React, { useState } from 'react';
import { Button, Group, Paper, Stack, Table, Text, Title } from '@mantine/core';
import { PatternItemset } from 'api/admin/data-mining/run-results';

function Itemset({ value }: { value: PatternItemset }) {
  const [page, setPage] = useState(0);
  const start = page * 50;
  return <Paper component="section" aria-label={'Itemset ' + value.itemsetOrder} withBorder p="md" radius="md">
    <Title order={4}>Itemset {value.itemsetOrder}</Title>
    <Text size="sm">Item {start + 1}–{Math.min(start + 50, value.items.length)} / {value.items.length}</Text>
    <div style={{ overflowX: 'auto' }}><Table aria-label={'Item của itemset ' + value.itemsetOrder} striped style={{ minWidth: 800 }}>
      <thead><tr><th>Thứ tự / Mã item</th><th>Sản phẩm snapshot</th><th>SKU snapshot</th><th>Trọng số item</th><th>Số lần xuất hiện</th><th>Số chuỗi chứa item</th></tr></thead>
      <tbody>{value.items.slice(start, start + 50).map(item => <tr key={item.itemOrder}>
        <td>{item.itemOrder} / {item.itemId}</td><td><Text style={{ overflowWrap: 'anywhere' }}>{item.productNameSnapshot || '(Tên trống trong snapshot)'}</Text>
          <Text size="xs" color="dimmed" style={{ overflowWrap: 'anywhere' }}>{item.productId}</Text></td>
        <td style={{ overflowWrap: 'anywhere' }}>{item.skuSnapshot || '(SKU trống trong snapshot)'}</td>
        <td>{item.itemWeight}</td><td>{item.occurrenceCount}</td><td>{item.sequenceSupport}</td>
      </tr>)}</tbody>
    </Table></div>
    {value.items.length > 50 && <Group mt="sm"><Button disabled={!page} onClick={() => setPage(n => n - 1)}>Item trước</Button>
      <Button disabled={start + 50 >= value.items.length} onClick={() => setPage(n => n + 1)}>Item tiếp</Button></Group>}
  </Paper>;
}

export default function PatternStructure({ itemsets }: { itemsets: PatternItemset[] }) {
  const [page, setPage] = useState(0);
  const start = page * 10;
  return <section aria-label="Cấu trúc temporal"><Stack>
    <Title order={3}>Cấu trúc itemset theo thứ tự</Title>
    <Text size="sm">Itemset {start + 1}–{Math.min(start + 10, itemsets.length)} / {itemsets.length}. Thứ tự itemset không biểu thị khoảng cách thời gian.
      Tên và SKU là snapshot lịch sử của run. Các thống kê item được tính trên dữ liệu đầu vào của run, không phải riêng các lần khớp pattern.</Text>
    {itemsets.slice(start, start + 10).map(set => <Itemset key={set.itemsetOrder} value={set}/>)}
    {itemsets.length > 10 && <Group><Button disabled={!page} onClick={() => setPage(n => n - 1)}>Itemset trước</Button>
      <Button disabled={start + 10 >= itemsets.length} onClick={() => setPage(n => n + 1)}>Itemset tiếp</Button></Group>}
  </Stack></section>;
}
