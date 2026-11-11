import React from 'react';
import { Alert, Container, Grid, Stack, Text, Title, useMantineTheme } from '@mantine/core';
import CategoryCard from '../../components/catalog/CategoryCard';
import useCatalogCategories from '../../hooks/catalog/useCatalogCategories';
import { categoryIcons } from '../client-home/ClientHomeFeaturedCategories';

export default function AllCategoriesPage() {
  const state = useCatalogCategories();
  const theme = useMantineTheme();
  return (
    <main>
      <Container size="xl">
        <Stack spacing={theme.spacing.xl * 1.5}>
          <Title order={1}>Danh mục sản phẩm</Title>
          {state.status === 'loading' ? <Text role="status">Đang tải danh mục…</Text>
            : state.status === 'error' ? <Alert role="alert" color="red">Không tải được danh mục. Vui lòng thử lại sau.</Alert>
              : state.categories.length === 0 ? <Text role="status">Chưa có danh mục có sản phẩm.</Text>
                : <Grid role="list" aria-label="Danh mục có sản phẩm">
                  {state.categories.map((category) => (
                    <Grid.Col role="listitem" key={category.categoryId} span={6} sm={4} md={3}>
                      <CategoryCard category={category} icon={categoryIcons.get((category.categoryName ?? '').trim().toLowerCase())}/>
                    </Grid.Col>
                  ))}
                </Grid>}
        </Stack>
      </Container>
    </main>
  );
}
