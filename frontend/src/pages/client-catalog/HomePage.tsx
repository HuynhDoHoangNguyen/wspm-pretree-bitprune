import React, { useEffect, useState } from 'react';
import { Alert, Anchor, Box, Container, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { getCategories, getLatestProducts } from '../../api/catalog/catalog';
import ProductCard, { ProductCardProps } from '../../components/catalog/ProductCard';
import { CategoryResponse, ProductResponse } from '../../models/catalog/catalog';

export interface HomePageProps {
  /** Only pass a banner asset/URL approved by the project owner. No default image is supplied. */
  banner?: { src: string; alt: string };
  getCategoryHref: (categoryId: string) => string;
  onCategoryClick?: (categoryId: string, event: React.MouseEvent<HTMLAnchorElement>) => void;
  productListHref: string;
  onViewAllProducts?: (event: React.MouseEvent<HTMLAnchorElement>) => void;
  getProductHref: ProductCardProps['getProductHref'];
  onProductClick?: ProductCardProps['onProductClick'];
}

type Resource<T> = { status: 'loading' } | { status: 'error' } | { status: 'success'; data: readonly T[] };

export default function HomePage({
  banner, getCategoryHref, onCategoryClick, productListHref, onViewAllProducts,
  getProductHref, onProductClick,
}: HomePageProps) {
  const [categories, setCategories] = useState<Resource<CategoryResponse>>({ status: 'loading' });
  const [latest, setLatest] = useState<Resource<ProductResponse>>({ status: 'loading' });
  const [failedBannerSrc, setFailedBannerSrc] = useState<string | null>(null);
  const showBanner = !!banner?.src.trim() && banner.src !== failedBannerSrc;

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    getCategories(controller.signal).then((data) => {
      if (active) setCategories({ status: 'success', data });
    }).catch(() => {
      if (active && !controller.signal.aborted) setCategories({ status: 'error' });
    });
    getLatestProducts(controller.signal).then((data) => {
      // Keep the backend's order; never sort timestamps or mutate the response here.
      if (active) setLatest({ status: 'success', data: data.slice(0, 5) });
    }).catch(() => {
      if (active && !controller.signal.aborted) setLatest({ status: 'error' });
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, []);

  return (
    <Container role="region" aria-label="Trang chủ" size="xl" py="xl"
      sx={(theme) => ({ backgroundColor: theme.white })}>
      <Stack spacing="xl">
        <Box component="section" aria-label="Giới thiệu" p="xl"
          sx={(theme) => ({ backgroundColor: theme.colors.blue[0], borderRadius: theme.radius.md })}>
          <Stack spacing="md">
            <Title order={1} color="blue">Khám phá sản phẩm</Title>
            <Text color="blue">Tìm sản phẩm theo danh mục và khám phá những sản phẩm mới nhất.</Text>
            {showBanner && banner && (
              <img src={banner.src} alt={banner.alt}
                onError={() => setFailedBannerSrc(banner.src)}
                style={{ display: 'block', width: '100%', maxHeight: 360, objectFit: 'contain', borderRadius: 8 }}/>
            )}
          </Stack>
        </Box>

        <Box component="section" aria-label="Danh mục sản phẩm">
          <Stack spacing="md">
            <Title order={2} color="blue">Danh mục sản phẩm</Title>
            {categories.status === 'loading' ? (
              <Text role="status" color="blue">Đang tải danh mục…</Text>
            ) : categories.status === 'error' ? (
              <Alert role="alert" color="red" title="Không tải được danh mục">Vui lòng thử lại sau.</Alert>
            ) : categories.data.length === 0 ? (
              <Text role="status" color="dimmed">Chưa có danh mục.</Text>
            ) : (
              // There is no featured/ranking field in CategoryResponse: display every API category in API order.
              <SimpleGrid role="list" aria-label="Danh mục" cols={4} spacing="md"
                breakpoints={[{ maxWidth: 'sm', cols: 2 }, { maxWidth: 'xs', cols: 1 }]}>
                {categories.data.map((category) => (
                  <Box role="listitem" key={category.categoryId}>
                    <Anchor href={getCategoryHref(category.categoryId)}
                      onClick={(event: React.MouseEvent<HTMLAnchorElement>) => onCategoryClick?.(category.categoryId, event)}
                      color="blue" weight={600} underline={false} p="md"
                      sx={(theme) => ({
                        display: 'block', height: '100%', border: `1px solid ${theme.colors.blue[1]}`,
                        borderRadius: theme.radius.md, overflowWrap: 'anywhere',
                        '&:focus-visible': { outline: `2px solid ${theme.colors.blue[6]}`, outlineOffset: 2 },
                      })}>
                      {category.categoryName?.trim() ? category.categoryName : 'Danh mục chưa có tên'}
                    </Anchor>
                  </Box>
                ))}
              </SimpleGrid>
            )}
          </Stack>
        </Box>

        <Box component="section" aria-label="Sản phẩm mới nhất">
          <Stack spacing="md">
            <Title order={2} color="blue">Sản phẩm mới nhất</Title>
            {latest.status === 'loading' ? (
              <Text role="status" color="blue">Đang tải sản phẩm mới nhất…</Text>
            ) : latest.status === 'error' ? (
              <Alert role="alert" color="red" title="Không tải được sản phẩm mới nhất">Vui lòng thử lại sau.</Alert>
            ) : latest.data.length === 0 ? (
              <Text role="status" color="dimmed">Chưa có sản phẩm mới.</Text>
            ) : (
              <SimpleGrid role="list" aria-label="Sản phẩm mới" cols={5} spacing="lg"
                breakpoints={[{ maxWidth: 'md', cols: 3 }, { maxWidth: 'sm', cols: 2 }, { maxWidth: 'xs', cols: 1 }]}>
                {latest.data.map((product) => (
                  <Box role="listitem" key={product.productId}>
                    <ProductCard product={product} getProductHref={getProductHref} onProductClick={onProductClick}/>
                  </Box>
                ))}
              </SimpleGrid>
            )}
            <Anchor href={productListHref} onClick={onViewAllProducts} color="blue" weight={600}>
              Xem tất cả sản phẩm
            </Anchor>
          </Stack>
        </Box>
      </Stack>
    </Container>
  );
}
