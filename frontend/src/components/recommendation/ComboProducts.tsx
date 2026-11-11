import React, { useState } from 'react';
import { Anchor, Button, Checkbox, Group, Image, Paper, Stack, Text, TextInput } from '@mantine/core';
import { PublicCombo } from 'models/recommendation/combos';
import { displayMoney } from 'api/recommendation/combos';

/** Current prices only, with exact decimal multiplication and summation. */
export function selectedTotal(items: PublicCombo['items'], quantities?: Readonly<Record<string, string>>): string | null {
  if (items.some(i => i.product.currentPrice === null)) return null;
  const scale = Math.max(2, ...items.map(i => i.product.currentPrice?.split('.')[1]?.length ?? 0));
  const sum = items.reduce((total, i) => {
    const [whole, fraction = ''] = (i.product.currentPrice ?? '0').split('.');
    return total + BigInt(whole + fraction.padEnd(scale, '0')) * BigInt(quantities?.[itemKey(i)] ?? i.quantity);
  }, BigInt(0)).toString().padStart(scale + 1, '0');
  return sum.slice(0, -scale) + '.' + sum.slice(-scale);
}

const itemKey = (item: PublicCombo['items'][number]) => `${item.itemsetOrder}:${item.itemOrder}:${item.product.productId}`;
const stockOf = (value: string | null) => typeof value === 'string' && /^(0|[1-9][0-9]*)$/.test(value) ? BigInt(value) : null;
const validQuantity = (value: string, maximum: bigint | null) => /^[1-9][0-9]*$/.test(value) && maximum !== null && BigInt(value) <= maximum;

export default function ComboProducts({ combo, suggestedProductIds, contextProductId, compact = false }: {
  combo: PublicCombo; suggestedProductIds?: string[]; contextProductId?: string; compact?: boolean;
}) {
  const items = suggestedProductIds ? combo.items.filter(i => suggestedProductIds.includes(i.product.productId)) : combo.items;
  const [selected, setSelected] = useState<string[]>([]);
  const [quantities, setQuantities] = useState<Record<string,string>>({});
  const chosen = items.filter(i => selected.includes(itemKey(i)) && (stockOf(i.product.availableQuantity) ?? BigInt(0)) > BigInt(0));
  const resolvedQuantities = Object.fromEntries(items.map(i => [itemKey(i), quantities[itemKey(i)] ?? '1']));
  const valid = chosen.every(i => validQuantity(resolvedQuantities[itemKey(i)],stockOf(i.product.availableQuantity)));
  return <Stack spacing="xs">
    {items.map(item => {
      const key = itemKey(item), stock = stockOf(item.product.availableQuantity);
      const available = stock !== null && stock > BigInt(0), checked = selected.includes(key) && available;
      const current = combo.kind === 'SAME_ORDER_BUNDLE' && contextProductId === item.product.productId;
      const quantity = resolvedQuantities[key];
      return <Paper key={key} withBorder radius="sm" p="xs" aria-current={current ? 'true' : undefined}
        data-selected={checked ? 'true' : 'false'} data-current-product={current ? 'true' : 'false'}
        sx={theme => ({ borderColor: current ? theme.colors.blue[5] : undefined, backgroundColor: current ? theme.colors.blue[0] : undefined })}>
        <Group noWrap spacing="xs">
          <Checkbox aria-label={'Chọn ' + item.product.name} checked={checked} disabled={!available}
            onChange={event => { const checked = event.currentTarget.checked; setSelected(previous => checked
              ? [...previous, key] : previous.filter(id => id !== key)); }}/>
          <Group noWrap spacing="xs" style={{ flex: 1, minWidth: 0, opacity: current || checked ? 1 : 0.45 }}>
            <Image src={item.product.imageUrl || undefined} alt={item.product.name} width={compact ? 44 : 64} height={compact ? 44 : 64} fit="contain" withPlaceholder/>
            <Stack spacing={2} style={{ minWidth: 0 }}>
              <Anchor href={'/products/' + encodeURIComponent(item.product.productId)} size="sm">{item.product.name}</Anchor>
              <Text size="sm" color="pink">{displayMoney(item.product.currentPrice, combo.pricing.currencyCode)}</Text>
              {!available && <Text size="xs" color="dimmed">{stock === null ? 'Chưa có thông tin tồn kho' : 'Hết hàng'}</Text>}
            </Stack>
          </Group>
        </Group>
        <TextInput type="number" inputMode="numeric" label={'Số lượng ' + item.product.name} value={quantity} min="1" max={stock?.toString()} step="1"
          disabled={!checked} mt="xs" styles={{ input: { maxWidth: 120 } }}
          error={checked && !validQuantity(quantity,stock) ? 'Nhập số lượng hợp lệ.' : undefined}
          onChange={event => { const value=event.currentTarget.value; if(value === '' || validQuantity(value,stock)) setQuantities(previous => ({ ...previous,[key]:value })); }}
          onBlur={() => { if(!validQuantity(quantity,stock))setQuantities(previous => ({ ...previous,[key]:'1' })); }}/>
      </Paper>;
    })}
    <Text color="pink" weight={600}>Tổng giá đang chọn: {valid ? displayMoney(selectedTotal(chosen,resolvedQuantities), combo.pricing.currencyCode) : '—'}</Text>
    <Button disabled>Thêm vào giỏ hàng</Button>
  </Stack>;
}
