import React, { useEffect, useState } from 'react';
import { Alert, Anchor, Box, Card, Container, SimpleGrid, Stack, Text, Title, useMantineTheme } from '@mantine/core';
import { CatalogApiError, getProduct } from '../api/catalog';
import { ProductDetailResponse } from '../models/catalog';
import ProductComboSection from '../components/ProductComboSection';

export interface ProductDetailPageProps {
  productId: string;
  productListHref: string;
  onBackToList?: (event: React.MouseEvent<HTMLAnchorElement>) => void;
}

type LoadState = { productId: string } & (
  | { status: 'loading' | 'not-found' | 'error' }
  | { status: 'success'; product: ProductDetailResponse }
);

const priceFormatter = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 20 });

/** Local gallery state is reset with the loaded product; image URLs are never rewritten. */
function ProductInformation({ product }: { product: ProductDetailResponse }) {
  const name = product.productName?.trim() ? product.productName : 'Sản phẩm chưa có tên';
  const [selectedUrl, setSelectedUrl] = useState<string | null>(null);
  const [failedUrls, setFailedUrls] = useState<readonly string[]>([]);
  const images: { url: string; alt: string }[] = [];
  const addImage = (url: string | null, alt: string | null) => {
    if (url?.trim() && !images.some((image) => image.url === url)) {
      images.push({ url, alt: alt?.trim() ? alt : name });
    }
  };
  const primaryMetadata = product.images.find((image) => image.imageUrl === product.imageUrl);
  addImage(product.imageUrl, primaryMetadata?.altText ?? null);
  product.images.forEach((image) => addImage(image.imageUrl, image.altText));
  const available = images.filter((image) => !failedUrls.includes(image.url));
  const selected = available.find((image) => image.url === selectedUrl) ?? available[0];
  const failImage = (url: string) => setFailedUrls((previous) => previous.includes(url) ? previous : [...previous, url]);

  return (
    <SimpleGrid cols={2} spacing="xl" breakpoints={[{ maxWidth: 'sm', cols: 1 }]}>
      <Stack spacing="md">
        <Box role="group" aria-label="Ảnh chính"
          sx={(theme) => ({
            aspectRatio: '1 / 1', backgroundColor: theme.colorScheme === 'dark' ? theme.colors.dark[6] : theme.colors.gray[0], borderRadius: theme.radius.md,
            display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden',
          })}>
          {selected ? (
            <img key={selected.url} src={selected.url} alt={selected.alt}
              onError={() => failImage(selected.url)}
              style={{ display: 'block', width: '100%', height: '100%', objectFit: 'contain' }}/>
          ) : (
            <Text role="img" aria-label={`Chưa có ảnh cho ${name}`} color="blue">Chưa có ảnh</Text>
          )}
        </Box>
        {images.length > 1 && (
          <SimpleGrid role="group" aria-label="Chọn ảnh sản phẩm" cols={4} spacing="xs">
            {images.map((image, index) => {
              const failed = failedUrls.includes(image.url);
              return (
                <Box component="button" type="button" key={image.url}
                  aria-label={`Xem ảnh ${index + 1}`} aria-pressed={selected?.url === image.url}
                  disabled={failed} onClick={() => setSelectedUrl(image.url)}
                  sx={(theme) => ({
                    padding: theme.spacing.xs, backgroundColor: theme.colorScheme === 'dark' ? theme.colors.dark[7] : theme.white, aspectRatio: '1 / 1',
                    borderRadius: theme.radius.sm,
                    border: `2px solid ${selected?.url === image.url ? theme.colors.blue[6] : theme.colors.blue[1]}`,
                    cursor: failed ? 'default' : 'pointer', opacity: failed ? 0.5 : 1,
                    '&:focus-visible': { outline: `2px solid ${theme.colors.blue[6]}`, outlineOffset: 2 },
                  })}>
                  {failed ? <Text size="xs" color="dimmed">Ảnh lỗi</Text> : (
                    <img src={image.url} alt="" loading="lazy" onError={() => failImage(image.url)}
                      style={{ display: 'block', width: '100%', height: '100%', objectFit: 'contain' }}/>
                  )}
                </Box>
              );
            })}
          </SimpleGrid>
        )}
      </Stack>
      <Card radius="md" shadow="sm" p="lg">
        <Stack spacing="md">
          <Title order={1}>{name}</Title>
          <Text color="pink" size="xl" weight={500}>
            {product.price === null ? 'Chưa có giá' : `${priceFormatter.format(product.price)} ₫`}
          </Text>
          <Box component="dl" sx={{ margin: 0, overflowWrap: 'anywhere', '& dd': { margin: '0 0 16px' } }}>
            <Text component="dt" weight={600}>Mã sản phẩm</Text>
            <Text component="dd">{product.productId}</Text>
            <Text component="dt" weight={600}>Mã danh mục</Text>
            <Text component="dd">{product.categoryId ?? 'Chưa có danh mục'}</Text>
            <Text component="dt" weight={600}>Trạng thái</Text>
            <Text component="dd">{product.productStatus ?? 'Chưa có thông tin'}</Text>
            <Text component="dt" weight={600}>Ngày tạo</Text>
            <Text component="dd">
              {product.createdAt === null ? 'Chưa có thông tin' : (
                <time dateTime={product.createdAt}>{product.createdAt}</time>
              )}
            </Text>
          </Box>
          {/* Combo cards require a verified public API/contract; no substitute data is rendered. */}
        </Stack>
      </Card>
    </SimpleGrid>
  );
}

export default function ProductDetailPage({ productId, productListHref, onBackToList }: ProductDetailPageProps) {
  const theme = useMantineTheme();
  const [state, setState] = useState<LoadState>({ productId, status: 'loading' });

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    setState({ productId, status: 'loading' });
    // The API client can reject invalid IDs synchronously as well as fail asynchronously.
    const load = async () => {
      try {
        const product = await getProduct(productId, controller.signal);
        if (active) setState({ productId, status: 'success', product });
      } catch (error) {
        if (active && !controller.signal.aborted) {
          setState({ productId, status: error instanceof CatalogApiError && error.status === 404 ? 'not-found' : 'error' });
        }
      }
    };
    void load();
    return () => {
      active = false;
      controller.abort();
    };
  }, [productId]);

  const loading = state.productId !== productId || state.status === 'loading';
  return (
    <Container role="region" aria-label="Chi tiết sản phẩm" size="xl">
      <Stack spacing={theme.spacing.xl * 1.5}>
        <Anchor href={productListHref} onClick={onBackToList} color="blue">Về danh sách sản phẩm</Anchor>
        {loading ? (
          <Text role="status" color="blue">Đang tải chi tiết sản phẩm…</Text>
        ) : state.status === 'not-found' ? (
          <Text role="status" color="dimmed">Không tìm thấy sản phẩm.</Text>
        ) : state.status === 'error' ? (
          <Alert role="alert" color="red" title="Không tải được chi tiết sản phẩm">Vui lòng thử lại sau.</Alert>
        ) : state.status === 'success' && (
          <React.Fragment key={state.productId}>
            <ProductInformation product={state.product}/>
            <ProductComboSection productId={state.product.productId}/>
          </React.Fragment>
        )}
      </Stack>
    </Container>
  );
}
