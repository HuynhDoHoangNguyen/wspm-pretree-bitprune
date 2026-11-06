import React from 'react';
import { Badge, Card, Group, Image, Stack, Text } from '@mantine/core';
import { DeviceLaptop } from 'tabler-icons-react';
import { CustomerProduct } from './CustomerCombos.types';

export const formatPrice = (price: number) => new Intl.NumberFormat('vi-VN', {
  style: 'currency', currency: 'VND', maximumFractionDigits: 0,
}).format(price);

export default function ProductSelectionCard({ product }: {
  product: CustomerProduct;
}) {
  return (
    <Card withBorder radius="lg" p="xl" className="customer-product" aria-label="Sản phẩm đang chọn">
      {product.imageUrl ? <Image src={product.imageUrl} alt={product.name} height={180} fit="contain" withPlaceholder/> : <div className="customer-product-image" role="img" aria-label={`Ảnh minh họa ${product.name}`}>
        <DeviceLaptop size={100} strokeWidth={1}/>
        <Text size="xs" color="dimmed">Ảnh minh họa</Text>
      </div>}
      <Stack spacing="sm" mt="lg">
        <Group><Badge color="blue" variant="light">{product.category}</Badge></Group>
        <Text color="dimmed" size="sm">Mã sản phẩm: {product.code}</Text>
        <Text component="h2" size="xl" weight={700} m={0}>{product.name}</Text>
        <Text color="blue" size="xl" weight={700}>{formatPrice(product.price)}</Text>
        <Text size="sm" color="dimmed">Chọn một sản phẩm để khám phá các combo gợi ý.</Text>
      </Stack>
    </Card>
  );
}
