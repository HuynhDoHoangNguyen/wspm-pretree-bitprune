import React from 'react';
import { Badge, Card, Group, Stack, Text, Title } from '@mantine/core';
import { PublicCombo } from 'models/recommendation/combos';
import ComboProducts from './ComboProducts';
import ComboPopupLink from './ComboPopupLink';

export const comboKindLabel: Record<PublicCombo['kind'], string> = {
  SAME_ORDER_BUNDLE: 'Mua cùng đơn', SEQUENTIAL_CROSS_SELL: 'Mua theo thứ tự', MIXED_SEQUENCE: 'Mua kết hợp',
};

export { selectedTotal } from './ComboProducts';
export default function ComboSelection({ combo, suggestedProductIds, matchedItemsets, contextProductId, compact = false }: {
  combo: PublicCombo; suggestedProductIds?: string[]; matchedItemsets?: number; contextProductId?: string; compact?: boolean;
}) {
  if (combo.kind !== 'SAME_ORDER_BUNDLE' && !suggestedProductIds?.length) return null;
  return <Card component="article" withBorder radius="md" p={compact ? 'sm' : 'md'}
    sx={theme => ({ overflowWrap: 'anywhere', borderColor: theme.colors.blue[2], backgroundColor: theme.colorScheme === 'dark' ? theme.colors.dark[7] : theme.colors.blue[0] })}>
    <Stack spacing="sm">
      <Group position="apart" spacing={4}><Title order={3} sx={theme => ({ fontSize: compact ? 14 : 18, color: theme.colors.blue[theme.colorScheme === 'dark' ? 3 : 8] })}>{combo.name}</Title>{combo.kind !== 'SAME_ORDER_BUNDLE' && <Badge size="xs">{comboKindLabel[combo.kind]}</Badge>}</Group>
      {matchedItemsets ? <Text size="xs" color="teal">Dựa trên lịch sử mua: đã khớp {matchedItemsets} bước. Gợi ý cho lần mua tiếp theo.</Text> : null}
      <ComboProducts key={combo.comboId + ':' + (contextProductId ?? '') + ':' + (suggestedProductIds?.join(',') ?? 'public')} combo={combo} suggestedProductIds={suggestedProductIds} contextProductId={contextProductId} compact={compact}/>
      <ComboPopupLink comboId={combo.comboId} productId={contextProductId}/>
    </Stack>
  </Card>;
}
