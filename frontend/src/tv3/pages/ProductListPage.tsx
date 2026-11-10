import React, { useEffect, useState } from 'react';
import { Alert, Box, Container, Grid, Group, Pagination, Stack, Text, Title, useMantineTheme } from '@mantine/core';
import { getProducts } from '../api/catalog';
import ProductCard, { ProductCardProps } from '../components/ProductCard';
import { ProductResponse } from '../models/catalog';

export interface ProductListPageProps {
  /** null/undefined selects all products; supplied IDs are compared without conversion. */
  categoryId?: string | null;
  getProductHref: ProductCardProps['getProductHref'];
  onProductClick?: ProductCardProps['onProductClick'];
  page?: number;
  onPageChange?: (page: number) => void;
}

type LoadState = { categoryId: string | null } & (
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'success'; products: readonly ProductResponse[] }
);

export default function ProductListPage({ categoryId, getProductHref, onProductClick, page, onPageChange }: ProductListPageProps) {
  const selectedCategory = categoryId ?? null;
  const theme = useMantineTheme();
  const [localPage, setLocalPage] = useState({ categoryId: selectedCategory, page: 1 });
  const [state, setState] = useState<LoadState>({ categoryId: selectedCategory, status: 'loading' });

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setState({ categoryId: selectedCategory, status: 'loading' });

    getProducts(controller.signal).then((products) => {
      if (!active) return;
      setState({
        categoryId: selectedCategory,
        status: 'success',
        products: selectedCategory === null
          ? products
          : products.filter((product) => product.categoryId === selectedCategory),
      });
    }).catch(() => {
      if (active && !controller.signal.aborted) {
        setState({ categoryId: selectedCategory, status: 'error' });
      }
    });

    return () => {
      active = false;
      controller.abort();
    };
  }, [selectedCategory]);

  // Hide previous results during the render before the new category's effect runs.
  const loading = state.categoryId !== selectedCategory || state.status === 'loading';
  const requestedPage = page ?? (localPage.categoryId === selectedCategory ? localPage.page : 1);
  const totalPages = state.status === 'success' ? Math.ceil(state.products.length / 12) : 0;
  const currentPage = Math.min(Number.isSafeInteger(requestedPage) && requestedPage > 0 ? requestedPage : 1, Math.max(1, totalPages));
  const visibleProducts = state.status === 'success' ? state.products.slice((currentPage - 1) * 12, currentPage * 12) : [];

  return (
    <Container role="region" aria-label="Danh sách sản phẩm" size="xl">
      <Stack spacing={theme.spacing.xl * 1.5}>
        <Box>
          <Title order={1}>Danh sách sản phẩm</Title>
          <Text color="dimmed">{selectedCategory === null ? 'Tất cả sản phẩm' : 'Sản phẩm theo danh mục'}</Text>
        </Box>
        {loading ? (
          <Text role="status" color="blue">Đang tải sản phẩm…</Text>
        ) : state.status === 'error' ? (
          <Alert role="alert" color="red" title="Không tải được sản phẩm">
            Vui lòng thử lại sau.
          </Alert>
        ) : state.status === 'success' && (
          state.products.length === 0 ? (
            <Text role="status" color="dimmed">
              {selectedCategory === null ? 'Chưa có sản phẩm.' : 'Không có sản phẩm trong danh mục này.'}
            </Text>
          ) : (
            <>
              <Grid role="list" aria-label="Sản phẩm">
                {visibleProducts.map((product) => (
                  <Grid.Col role="listitem" key={product.productId} span={6} sm={4} md={3}>
                    <ProductCard product={product} getProductHref={getProductHref} onProductClick={onProductClick}/>
                  </Grid.Col>
                ))}
              </Grid>
              <Group position="apart">
                {totalPages > 1 && (
                  <Pagination page={currentPage} total={totalPages} onChange={(nextPage) => {
                    setLocalPage({ categoryId: selectedCategory, page: nextPage });
                    onPageChange?.(nextPage);
                  }}/>
                )}
                <Text>Trang {currentPage} / {totalPages} · {state.products.length} sản phẩm</Text>
              </Group>
            </>
          )
        )}
      </Stack>
    </Container>
  );
}
