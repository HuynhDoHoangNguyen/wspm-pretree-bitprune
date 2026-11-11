import React from 'react';
import { Alert, Button, Grid, Group, Stack, Text, Title } from '@mantine/core';
import { Backpack, Cpu, DeviceDesktop, DeviceGamepad, DeviceLaptop, DeviceSpeaker, Icon, Keyboard, List, Mouse } from 'tabler-icons-react';
import { Link } from 'react-router-dom';
import useCatalogCategories from 'hooks/catalog/useCatalogCategories';
import CategoryCard from 'components/catalog/CategoryCard';

export const categoryIcons: ReadonlyMap<string, Icon> = new Map([
  ['balo', Backpack], ['bàn phím', Keyboard], ['chuột', Mouse], ['cpu', Cpu],
  ['laptop', DeviceLaptop], ['loa', DeviceSpeaker], ['máy chơi game', DeviceGamepad], ['pc', DeviceDesktop],
]);

function ClientHomeFeaturedCategories() {
  const state = useCatalogCategories();

  return (
    <Stack>
      <Group position="apart">
        <Title order={2}>
          <Text
            color="orange"
            inherit
          >
            Danh mục nổi bật
          </Text>
        </Title>
        <Button component={Link} to="/categories" variant="light" leftIcon={<List size={16}/>} radius="md">
          Xem tất cả
        </Button>
      </Group>
      {state.status === 'loading' ? <Text role="status">Đang tải danh mục…</Text>
        : state.status === 'error' ? <Alert role="alert" color="red">Không tải được danh mục. Vui lòng thử lại sau.</Alert>
          : state.categories.length === 0 ? <Text role="status">Chưa có danh mục có sản phẩm.</Text>
            : <Grid>
              {state.categories.map((category) => (
                <Grid.Col key={category.categoryId} span={6} sm={4} md={3}>
                  <CategoryCard category={category} icon={categoryIcons.get(category.categoryName?.trim().toLowerCase() || '') || List}/>
                </Grid.Col>
              ))}
            </Grid>}
    </Stack>
  );
}

export default ClientHomeFeaturedCategories;
