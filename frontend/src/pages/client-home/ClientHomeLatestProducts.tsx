import React from 'react';
import { Button, Grid, Group, Skeleton, Stack, Text, Title, useMantineTheme } from '@mantine/core';
import { AlertTriangle, List, Marquee } from 'tabler-icons-react';
import { Link } from 'react-router-dom';
import useLatestProducts from 'hooks/catalog/useLatestProducts';
import ProductCard from 'components/catalog/ProductCard';

function ClientHomeLatestProducts() {
  const theme = useMantineTheme();

  const state = useLatestProducts();

  let resultFragment;

  if (state.status === 'loading') {
    resultFragment = (
      <Stack role="status" aria-label="Đang tải sản phẩm mới nhất">
        {Array(5).fill(0).map((_, index) => (
          <Skeleton key={index} height={50} radius="md"/>
        ))}
      </Stack>
    );
  }

  if (state.status === 'error') {
    resultFragment = (
      <Stack role="alert" my={theme.spacing.xl} sx={{ alignItems: 'center', color: theme.colors.pink[6] }}>
        <AlertTriangle size={125} strokeWidth={1}/>
        <Text size="xl" weight={500}>Đã có lỗi xảy ra</Text>
      </Stack>
    );
  }

  if (state.status === 'success' && state.products.length === 0) {
    resultFragment = (
      <Stack role="status" my={theme.spacing.xl} sx={{ alignItems: 'center', color: theme.colors.blue[6] }}>
        <Marquee size={125} strokeWidth={1}/>
        <Text size="xl" weight={500}>Không có sản phẩm</Text>
      </Stack>
    );
  }

  if (state.status === 'success' && state.products.length > 0) {
    resultFragment = (
      <Grid>
        {state.products.map((product) => (
          <Grid.Col key={product.productId} span={6} sm={4} md={3}>
            <ProductCard product={product} getProductHref={(id) => '/products/' + encodeURIComponent(id)}/>
          </Grid.Col>
        ))}
      </Grid>
    );
  }

  return (
    <Stack>
      <Group position="apart">
        <Title order={2}>
          <Text color="orange" inherit>
            Sản phẩm mới nhất
          </Text>
        </Title>
        <Button component={Link} to="/products" variant="light" leftIcon={<List size={16}/>} radius="md">
          Xem tất cả
        </Button>
      </Group>

      {resultFragment}
    </Stack>
  );
}

export default ClientHomeLatestProducts;
