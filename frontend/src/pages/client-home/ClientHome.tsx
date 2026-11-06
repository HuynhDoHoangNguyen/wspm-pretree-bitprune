import React, { useEffect, useState } from 'react';
import { Alert, Badge, Button, Card, Container, Image, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { Link } from 'react-router-dom';
import useTitle from 'hooks/use-title';
import { CatalogProduct, loadCatalog } from './CustomerCatalog.api';

type State = { status: 'loading' } | { status: 'error' } | { status: 'success'; products: CatalogProduct[] };
const price = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' });
const statusLabels: Record<string, string> = { ACTIVE: 'Đang kinh doanh', INACTIVE: 'Ngừng kinh doanh', DISCONTINUED: 'Ngừng kinh doanh', DRAFT: 'Bản nháp' };

export default function ClientHome() {
  useTitle('Sản phẩm');
  const [state, setState] = useState<State>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setState({ status: 'loading' });
    loadCatalog(controller.signal).then(products => {
      if (!controller.signal.aborted) setState({ status: 'success', products });
    }).catch(() => {
      if (!controller.signal.aborted) setState({ status: 'error' });
    });
    return () => controller.abort();
  }, [attempt]);
  return <Container size="xl" py="xl">
    <Stack spacing="lg">
      <Title order={1}>Sản phẩm</Title>
      {state.status === 'loading' && <Text role="status">Đang tải sản phẩm…</Text>}
      {state.status === 'error' && <Alert color="red" role="alert" title="Không tải được sản phẩm">
        <Button variant="light" color="blue" onClick={() => setAttempt(value => value + 1)}>Thử lại</Button>
      </Alert>}
      {state.status === 'success' && (state.products.length === 0
        ? <Text role="status">Chưa có sản phẩm.</Text>
        : <SimpleGrid cols={4} breakpoints={[{ maxWidth: 'md', cols: 3 }, { maxWidth: 'sm', cols: 2 }, { maxWidth: 'xs', cols: 1 }]}>
          {state.products.map(product => <Card component="article" key={product.productId} withBorder radius="md" p="md">
            <Image src={product.imageUrl || undefined} alt={product.productName} height={180} fit="contain" withPlaceholder/>
            <Stack spacing="xs" mt="md">
              <Text weight={600}>{product.productName}</Text>
              <Text color="blue" weight={700}>{price.format(product.price)}</Text>
              <Badge color={product.productStatus === 'ACTIVE' ? 'blue' : 'gray'}>{statusLabels[product.productStatus] || product.productStatus}</Badge>
              <Button component={Link} to={'/products/' + encodeURIComponent(product.productId) + '/combos'} color="blue" variant="light">Xem Combo gợi ý</Button>
            </Stack>
          </Card>)}
        </SimpleGrid>)}
    </Stack>
  </Container>;
}
