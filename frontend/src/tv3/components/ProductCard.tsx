import React, { useState } from 'react';
import { Anchor, Box, Card, Stack, Text } from '@mantine/core';
import { ProductResponse } from '../models/catalog';

export interface ProductCardProps {
  product: ProductResponse;
  /** The integrating page owns the route; the exact string ID is passed unchanged. */
  getProductHref: (productId: string) => string;
  onProductClick?: (productId: string, event: React.MouseEvent<HTMLAnchorElement>) => void;
}

const priceFormatter = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 });

export default function ProductCard({ product, getProductHref, onProductClick }: ProductCardProps) {
  const [failedImageUrl, setFailedImageUrl] = useState<string | null>(null);
  const name = product.productName?.trim() ? product.productName : 'Sản phẩm chưa có tên';
  const imageUrl = product.imageUrl;
  const showImage = !!imageUrl?.trim() && imageUrl !== failedImageUrl;
  const price = product.price === null ? 'Chưa có giá' : `${priceFormatter.format(product.price)} ₫`;

  return (
    <Card component="article" radius="md" shadow="sm" p="lg"
      sx={(theme) => ({
        height: '100%', transition: 'box-shadow .2s ease-in',
        '&:hover': {
          backgroundColor: theme.colorScheme === 'dark' ? theme.colors.dark[5] : 'unset',
          boxShadow: theme.shadows.lg,
        },
      })}>
      <Anchor href={getProductHref(product.productId)} underline={false}
        aria-label={`Xem sản phẩm ${name}`}
        onClick={(event: React.MouseEvent<HTMLAnchorElement>) => onProductClick?.(product.productId, event)}
        sx={(theme) => ({
          display: 'block',
          '&:focus-visible': { outline: `2px solid ${theme.colors.blue[6]}`, outlineOffset: 4 },
        })}>
        <Stack spacing="xs">
          <Box sx={(theme) => ({
            aspectRatio: '1 / 1', backgroundColor: theme.colorScheme === 'dark' ? theme.colors.dark[6] : theme.colors.gray[0], borderRadius: theme.radius.md,
            display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden',
          })}>
            {showImage ? (
              <img src={imageUrl || undefined} alt={name} loading="lazy"
                style={{ width: '100%', height: '100%', objectFit: 'contain' }}
                onError={() => setFailedImageUrl(imageUrl)}/>
            ) : (
              <Text color="blue" size="sm" role="img" aria-label={`Chưa có ảnh cho ${name}`}>Chưa có ảnh</Text>
            )}
          </Box>
          <Text weight={500}>{name}</Text>
          <Text color="pink" weight={500}>{price}</Text>
        </Stack>
      </Anchor>
    </Card>
  );
}
