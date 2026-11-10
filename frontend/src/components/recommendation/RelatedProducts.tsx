import React, { useEffect, useState } from 'react';
import { SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { getProducts } from 'api/catalog/catalog';
import { ProductResponse } from 'models/catalog/catalog';
import ProductCard from 'components/catalog/ProductCard';

export default function RelatedProducts({ productId, categoryId }: { productId: string; categoryId: string | null }) {
  const [state, setState] = useState<{ key: string; products?: readonly ProductResponse[]; error?: boolean }>({ key: '' });
  const key = JSON.stringify([productId, categoryId]);
  useEffect(() => {
    let active = true; const controller = new AbortController(); setState({ key });
    const load = async () => {
      try {
        const products = categoryId === null ? [] : (await getProducts(controller.signal))
          .filter(p => p.productId !== productId && p.categoryId === categoryId && p.productStatus === 'ACTIVE').slice(0, 4);
        if (active) setState({ key, products });
      } catch { if (active && !controller.signal.aborted) setState({ key, error: true }); }
    };
    void load(); return () => { active = false; controller.abort(); };
  }, [key, productId, categoryId]);
  const current = state.key === key;
  return <section aria-label="Sản phẩm liên quan"><Stack>
    <Title order={2}>Sản phẩm liên quan</Title>
    {current && state.error ? <Text color="dimmed">Chưa tải được sản phẩm liên quan.</Text>
      : !current || !state.products ? <Text>Đang tải sản phẩm liên quan…</Text>
        : state.products.length === 0 ? <Text color="dimmed">Chưa có sản phẩm khác trong danh mục này.</Text>
          : <SimpleGrid cols={4} breakpoints={[{ maxWidth: 'sm', cols: 2 }]}>{state.products.map(product => <ProductCard key={product.productId}
            product={product} getProductHref={id => '/products/' + encodeURIComponent(id)}/>)}</SimpleGrid>}
  </Stack></section>;
}
