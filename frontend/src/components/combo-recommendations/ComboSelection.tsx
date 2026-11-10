import React, { useState } from 'react';
import { Link, useInRouterContext } from 'react-router-dom';
import { Anchor, Badge, Card, Checkbox, Group, Image, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { PublicCombo } from 'tv3/models/combos';
import { displayMoney } from 'tv3/api/combos';

export const comboKindLabel: Record<PublicCombo['kind'], string> = {
  SAME_ORDER_BUNDLE: 'Mua cùng đơn', SEQUENTIAL_CROSS_SELL: 'Mua theo thứ tự', MIXED_SEQUENCE: 'Mua kết hợp',
};

/** Exact decimal arithmetic over current catalog prices; never applies a bundle discount. */
export function selectedTotal(items: PublicCombo['items']): string | null {
  if (items.some(i => i.product.currentPrice === null)) return null;
  const scale = Math.max(2, ...items.map(i => i.product.currentPrice?.split('.')[1]?.length ?? 0));
  const sum = items.reduce((total, i) => {
    const [whole, fraction = ''] = (i.product.currentPrice ?? '0').split('.');
    return total + BigInt(whole + fraction.padEnd(scale, '0')) * BigInt(i.quantity);
  }, BigInt(0)).toString().padStart(scale + 1, '0');
  return sum.slice(0, -scale) + '.' + sum.slice(-scale);
}

export default function ComboSelection({ combo, suggestedProductIds, matchedItemsets, contextProductId, compact = false }: {
  combo: PublicCombo; suggestedProductIds?: string[]; matchedItemsets?: number; contextProductId?: string; compact?: boolean;
}) {
  const inRouter = useInRouterContext();
  const comboHref = '/combos/' + encodeURIComponent(combo.comboId) + (contextProductId ? '?' + new URLSearchParams({ productId: contextProductId }) : '');
  const contents = suggestedProductIds ? combo.items.filter(i => suggestedProductIds.includes(i.product.productId)) : combo.items;
  const [selected, setSelected] = useState(() => contents.map(i => i.product.productId));
  const chosen = contents.filter(i => selected.includes(i.product.productId));
  const temporal = combo.kind !== 'SAME_ORDER_BUNDLE';
  return <Card component="article" withBorder radius="md" p={compact ? 'sm' : 'md'}
    sx={theme => ({ overflowWrap: 'anywhere', borderColor: theme.colors.blue[2], backgroundColor: theme.colorScheme === 'dark' ? theme.colors.dark[7] : theme.colors.blue[0] })}>
    <Stack spacing="sm">
      <Group position="apart" spacing={4}><Title order={3} sx={theme => ({ fontSize: compact ? 14 : 18, color: theme.colors.blue[theme.colorScheme === 'dark' ? 3 : 8] })}>{combo.name}</Title><Badge size="xs">{comboKindLabel[combo.kind]}</Badge></Group>
      {matchedItemsets ? <Text size="xs" color="teal">Dựa trên lịch sử mua: đã khớp {matchedItemsets} bước. Gợi ý cho lần mua tiếp theo.</Text>
        : temporal && <Text size="xs" color="dimmed">Tham khảo thứ tự mua bên dưới; chưa đối chiếu lịch sử mua của bạn.</Text>}
      <SimpleGrid cols={compact ? 1 : 2} spacing="xs" breakpoints={[{ maxWidth: 'sm', cols: 1 }]}>
        {contents.map(item => {
          const id = item.product.productId, checked = selected.includes(id);
          return <Card key={`${item.itemsetOrder}:${item.itemOrder}`} withBorder p="sm"
            sx={theme => ({ backgroundColor: checked ? (theme.colorScheme === 'dark' ? theme.colors.dark[6] : theme.colors.blue[0]) : undefined })}>
            <Group noWrap spacing="xs">
              <Checkbox checked={checked} aria-label={`Chọn ${item.product.name}`} onChange={event => {
                const value = event.currentTarget.checked;
                setSelected(previous => value ? [...previous, id] : previous.filter(p => p !== id));
              }}/>
              <Group noWrap align="center">
                <Image src={item.product.imageUrl || undefined} alt={item.product.name} width={compact ? 44 : 64} height={compact ? 44 : 64} fit="contain" withPlaceholder/>
                <Stack spacing={2} sx={{ minWidth: 0 }}>
                  <Anchor href={'/products/' + encodeURIComponent(id)} size="sm">{item.product.name}</Anchor>
                  <Text size="sm" color="pink">{displayMoney(item.product.currentPrice, combo.pricing.currencyCode)} × {item.quantity}</Text>
                  {temporal && <Text size="xs" color="dimmed">Bước {item.itemsetOrder}{id === contextProductId ? ' · Sản phẩm đang xem' : ''}</Text>}
                </Stack>
              </Group>
            </Group>
          </Card>;
        })}
      </SimpleGrid>
      <Group position="apart">
        <Text size={compact ? 'sm' : 'md'} color="pink" weight={600}>Tổng giá đang chọn: {displayMoney(selectedTotal(chosen), combo.pricing.currencyCode)}</Text>
        {inRouter ? <Anchor component={Link} to={comboHref}>Xem chi tiết Combo</Anchor>
          : <Anchor href={comboHref}>Xem chi tiết Combo</Anchor>}
      </Group>
      <Text size="xs" color="dimmed">Giá hiện tại, chưa áp dụng ưu đãi combo. Chưa tạo đơn hàng.</Text>
    </Stack>
  </Card>;
}
