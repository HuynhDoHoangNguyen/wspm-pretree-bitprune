import React from 'react';
import { Badge, Button, Card, Group, Stack, Text } from '@mantine/core';
import { ArrowRight, Package } from 'tabler-icons-react';
import { CustomerCombo } from './CustomerCombos.types';
import { formatPrice } from './ProductSelectionCard';

export default function ComboRecommendationCard({ combo, onViewDetail }: {
  combo: CustomerCombo;
  onViewDetail?: (comboId: string) => void;
}) {
  const hasDiscount = Number.isFinite(combo.referencePrice) && Number.isFinite(combo.price) &&
    combo.price >= 0 && (combo.referencePrice as number) > combo.price;
  return (
    <Card component="article" withBorder radius="lg" p="lg" className="customer-combo" aria-label={combo.name}>
      <Stack spacing="md" style={{ height: '100%' }}>
        <Group position="apart">
          <Badge color="blue" variant="light">Combo · {combo.items.length} sản phẩm</Badge>
          <Package color="#1971c2" size={22}/>
        </Group>
        <Text component="h3" size="lg" weight={700} m={0}>{combo.name}</Text>
        <ul className="customer-combo-items">
          {combo.items.map(product => <li key={product.productId}>{product.name}</li>)}
        </ul>
        {(combo.supportCount !== undefined || combo.patternWeight !== undefined) && (
          <Text size="xs" color="dimmed">
            {combo.supportCount !== undefined && `Số chuỗi hỗ trợ (minh họa): ${combo.supportCount}`}
            {combo.patternWeight !== undefined && ` · Trọng số mẫu (minh họa): ${combo.patternWeight}`}
          </Text>
        )}
        <Stack spacing={4} style={{ marginTop: 'auto' }}>
          {hasDiscount && <Text size="sm" color="dimmed">Giá tham chiếu: <s>{formatPrice(combo.referencePrice as number)}</s></Text>}
          <Text size="xl" color="blue" weight={700}>{formatPrice(combo.price)}</Text>
          {hasDiscount && <Text size="sm" color="blue">Tiết kiệm {formatPrice((combo.referencePrice as number) - combo.price)}</Text>}
        </Stack>
        <Button variant="light" color="blue" radius="md" rightIcon={<ArrowRight size={16}/>}
          disabled={!onViewDetail} onClick={() => onViewDetail?.(combo.comboId)}
          aria-label={`Xem chi tiết ${combo.name}`}>Xem chi tiết</Button>
        {!onViewDetail && <Text size="xs" color="dimmed">Chi tiết combo sẽ được kết nối ở TV3-02.</Text>}
      </Stack>
    </Card>
  );
}
