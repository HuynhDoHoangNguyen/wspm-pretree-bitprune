import { useMediaQuery } from '@mantine/hooks';
import React, { useEffect, useState } from 'react';
import { Alert, Box, Container, Grid, Group, Pagination, Stack, Text, Title, useMantineTheme, Card, TextInput, Select, Button } from '@mantine/core';
import LatestCombos from 'components/combo-recommendations/LatestCombos';
import PersonalRecommendations from 'components/combo-recommendations/PersonalRecommendations';
import { getCategories, getProducts } from '../api/catalog';
import ProductCard, { ProductCardProps } from '../components/ProductCard';
import { CategoryResponse, ProductResponse } from '../models/catalog';

export interface CatalogFilters { q: string; categoryId: string; minPrice: string; maxPrice: string; sort: string }
const emptyFilters: CatalogFilters = { q: '', categoryId: '', minPrice: '', maxPrice: '', sort: 'name' };
export interface ProductListPageProps {
  filters?: CatalogFilters;
  onFiltersChange?: (filters: CatalogFilters) => void;
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

export default function ProductListPage({ categoryId, getProductHref, onProductClick, page, onPageChange, filters, onFiltersChange }: ProductListPageProps) {
  const [localFilters, setLocalFilters] = useState(emptyFilters);
  const values = filters ?? localFilters;
  const selectedCategory = categoryId ?? (values.categoryId || null);
  const [categories, setCategories] = useState<readonly CategoryResponse[]>([]);
  useEffect(() => {
    let active = true; const controller = new AbortController();
    const load = async () => { try { const result = await getCategories(controller.signal); if (active) setCategories(result); } catch { /* Product list remains usable when categories cannot load. */ } };
    void load(); return () => { active = false; controller.abort(); };
  }, []);
  const change = (patch: Partial<CatalogFilters>) => {
    const next = { ...values, ...patch }; setLocalFilters(next); onFiltersChange?.(next);
    setLocalPage({ categoryId: next.categoryId || null, page: 1 });
  };
  const theme = useMantineTheme();
  const wide = useMediaQuery('(min-width: 992px)');
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
  const filtered = state.status !== 'success' ? [] : state.products.filter(p =>
    (p.productName ?? '').toLocaleLowerCase('vi').includes(values.q.trim().toLocaleLowerCase('vi'))
    && (!values.minPrice || (p.price !== null && p.price >= Number(values.minPrice)))
    && (!values.maxPrice || (p.price !== null && p.price <= Number(values.maxPrice))));
  const sorted = [...filtered].sort((a, b) => values.sort === 'price-asc' ? (a.price ?? Infinity) - (b.price ?? Infinity)
    : values.sort === 'price-desc' ? (b.price ?? -Infinity) - (a.price ?? -Infinity)
      : values.sort === 'latest' ? (b.createdAt ?? '').localeCompare(a.createdAt ?? '') || a.productId.localeCompare(b.productId)
        : (a.productName ?? '').localeCompare(b.productName ?? '', 'vi', { numeric: true }));
  const totalPages = Math.ceil(sorted.length / 12);
  const currentPage = Math.min(Number.isSafeInteger(requestedPage) && requestedPage > 0 ? requestedPage : 1, Math.max(1, totalPages));
  const visibleProducts = state.status === 'success' ? sorted.slice((currentPage - 1) * 12, currentPage * 12) : [];

  return (
    <Container role="region" aria-label="Danh sách sản phẩm" size="xl">
      <Stack spacing={theme.spacing.xl * 1.5}>
        <Box>
          <Title order={1}>Danh sách sản phẩm</Title>
          <Text color="dimmed">{selectedCategory === null ? 'Tất cả sản phẩm' : 'Sản phẩm theo danh mục'}</Text>
        </Box>
        <Grid>
          <Grid.Col span={12} md={3}>
            <Stack spacing="lg">
              <Card withBorder radius="md" p="md">
                <Box component="details" open={wide} sx={{ '& summary': { cursor: 'pointer', fontWeight: 600 } }}>
                  <summary>Bộ lọc sản phẩm</summary>
                  <Stack spacing="sm" mt="md">
                    <TextInput label="Tên sản phẩm" value={values.q} onChange={e => change({ q: e.currentTarget.value })}/>
                    <Select label="Danh mục" value={selectedCategory ?? ''} data={[{ value: '', label: 'Tất cả danh mục' }, ...categories.map(c => ({ value: c.categoryId, label: c.categoryName || c.categoryId }))]}
                      onChange={value => change({ categoryId: value ?? '' })}/>
                    <TextInput type="number" min="0" label="Giá từ" value={values.minPrice} onChange={e => change({ minPrice: e.currentTarget.value })}/>
                    <TextInput type="number" min="0" label="Giá đến" value={values.maxPrice} onChange={e => change({ maxPrice: e.currentTarget.value })}/>
                    <Button variant="light" onClick={() => change(emptyFilters)}>Bỏ bộ lọc</Button>
                  </Stack>
                </Box>
              </Card>
              <LatestCombos/>
            </Stack>
          </Grid.Col>
          <Grid.Col span={12} md={9}>
            <Stack>
              <Select label="Sắp xếp sản phẩm" value={values.sort} onChange={value => change({ sort: value ?? 'name' })}
                data={[{ value: 'name', label: 'Tên A–Z' }, { value: 'latest', label: 'Mới nhất' }, { value: 'price-asc', label: 'Giá tăng dần' }, { value: 'price-desc', label: 'Giá giảm dần' }]}/>
              {loading ? (
                <Text role="status" color="blue">Đang tải sản phẩm…</Text>
              ) : state.status === 'error' ? (
                <Alert role="alert" color="red" title="Không tải được sản phẩm">
                  Vui lòng thử lại sau.
                </Alert>
              ) : state.status === 'success' && (
                sorted.length === 0 ? (
                  <Text role="status" color="dimmed">
                    {selectedCategory === null ? 'Chưa có sản phẩm.' : 'Không có sản phẩm trong danh mục này.'}
                  </Text>
                ) : (
                  <>
                    <Grid role="list" aria-label="Sản phẩm">
                      {visibleProducts.map((product) => (
                        <Grid.Col role="listitem" key={product.productId} span={6} sm={4}>
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
                      <Text>Trang {currentPage} / {totalPages} · {sorted.length} sản phẩm</Text>
                    </Group>
                  </>
                )
              )}
            </Stack>
          </Grid.Col>
        </Grid>
        <PersonalRecommendations/>
      </Stack>
    </Container>
  );
}
