import React from 'react';
import { Link } from 'react-router-dom';
import { Anchor, Badge, Box, Card, Group, Image, Stack, Text, Title } from '@mantine/core';
import { displayMoney } from '../api/combos';
import { comboKindLabel } from 'components/combo-recommendations/ComboSelection';
import { PublicCombo } from '../models/combos';

export function ComboPricing({ combo }: { combo: PublicCombo }) {
  const p = combo.pricing;
  const labels: Record<string, string> = {
    PERCENTAGE: 'Tỷ lệ giảm trên sản phẩm đủ điều kiện',
    FIXED_AMOUNT: 'Mức giảm trên sản phẩm đủ điều kiện',
    FIXED_PRICE: 'Giá cố định cho phần sản phẩm đủ điều kiện',
  };
  return <Stack spacing="xs">
    <Text color="dimmed">Giá tham chiếu: {displayMoney(p.referenceOriginalPrice, p.currencyCode)}</Text>
    <Text color="pink" weight={600}>Giá Combo tham chiếu: {displayMoney(p.referenceSalePrice, p.currencyCode)}</Text>
    {p.discountType !== 'NONE' && p.discountValue !== null && (
      <Text size="sm">{labels[p.discountType]}: {displayMoney(p.discountValue, p.discountType === 'PERCENTAGE' ? '%' : p.currencyCode)}</Text>
    )}
    {p.maxDiscountAmount !== null && <Text size="sm">Giảm tối đa: {displayMoney(p.maxDiscountAmount, p.currencyCode)}</Text>}
    <Text size="xs" color="dimmed">Giá tham chiếu lúc công bố, không phải báo giá thanh toán.</Text>
  </Stack>;
}

export default function ComboCard({ combo, rank, productId }: { combo: PublicCombo; rank: number; productId: string }) {
  const href = '/combos/' + encodeURIComponent(combo.comboId) + '?' + new URLSearchParams({ productId });
  return <Card component="article" radius="md" shadow="sm" p="lg" sx={{ height: '100%', overflowWrap: 'anywhere' }}>
    <Stack spacing="sm">
      <Badge color="blue">Gợi ý {rank} · {comboKindLabel[combo.kind]}</Badge>
      <Title order={3}>{combo.name}</Title>
      <Text size="xs" color="dimmed">Mã Combo: {combo.comboId}</Text>
      {combo.explanation && <Text size="sm">{combo.explanation}</Text>}
      <Box component="ul" sx={{ paddingLeft: 0, listStyle: 'none', margin: 0 }}>
        {combo.items.map(item => <li key={`${item.itemsetOrder}:${item.itemOrder}`}>
          <Group noWrap spacing="xs" py={4}>
            <Image src={item.product.imageUrl || undefined} alt={item.product.name} width={44} height={44} fit="contain" withPlaceholder/>
            <Text size="sm">{item.product.name} × {item.quantity}{combo.kind !== 'SAME_ORDER_BUNDLE' ? ` · Bước ${item.itemsetOrder}` : ''}</Text>
          </Group>
        </li>)}
      </Box>
      <ComboPricing combo={combo}/>
      <Text size="sm">Số chuỗi hỗ trợ: {combo.metrics.supportAbs}/{combo.metrics.sequenceCount} · Trọng số mẫu: {combo.metrics.patternWeight}</Text>
      <Anchor component={Link} to={href} color="blue">Xem chi tiết Combo</Anchor>
    </Stack>
  </Card>;
}
