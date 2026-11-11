import React from 'react';
import { Card, Group, Text, useMantineTheme } from '@mantine/core';
import { Link } from 'react-router-dom';
import { Icon, List } from 'tabler-icons-react';
import { CategoryResponse } from '../../models/catalog/catalog';

export default function CategoryCard({ category, icon: CategoryIcon = List }: { category: CategoryResponse; icon?: Icon }) {
  const theme = useMantineTheme();
  return (
    <Card radius="md" shadow="sm" p="lg" component={Link}
      to={'/products?' + new URLSearchParams({ categoryId: category.categoryId })}
      sx={{ height: '100%', '&:hover': {
        backgroundColor: theme.colorScheme === 'dark' ? theme.colors.dark[5] : theme.colors.gray[0],
      } }}>
      <Group noWrap>
        <CategoryIcon size={50} strokeWidth={1} aria-hidden="true" style={{ flexShrink: 0 }}/>
        <Text sx={{ overflowWrap: 'anywhere' }}>
          {category.categoryName?.trim() ? category.categoryName : 'Danh mục chưa có tên'}
        </Text>
      </Group>
    </Card>
  );
}
