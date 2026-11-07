import React from 'react';
import { Anchor, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { Link } from 'react-router-dom';
import useTitle from 'hooks/use-title';
import DatasetExportForm from './DatasetExportForm';
import DatasetUploadForm from './DatasetUploadForm';
import useWorkspaceStyles from './workspace.styles';

export default function DatasetsPage() {
  useTitle('Dữ liệu khai phá');
  const { classes } = useWorkspaceStyles();
  return (
    <div className={classes.root}><Stack spacing="xl">
      <Anchor component={Link} to="/admin">Trang chủ Admin / Hướng dẫn thực hiện</Anchor>
      <Stack spacing="sm" className={classes.hero}>
        <Text size="sm" color="blue" weight={700}>BƯỚC 1 & 2 · CHUẨN BỊ DỮ LIỆU</Text>
        <Title order={2}>Dữ liệu khai phá</Title>
        <Text>Xuất lịch sử đơn hàng hoặc chọn CSV có sẵn, sau đó upload để tạo snapshot. Bạn sẽ kiểm tra và chuyển đổi dữ liệu tại trang chi tiết.</Text>
      </Stack>
      <SimpleGrid cols={2} spacing="lg" breakpoints={[{ maxWidth: 'md', cols: 1 }]} style={{ alignItems: 'start' }}>
        <DatasetExportForm/>
        <DatasetUploadForm/>
      </SimpleGrid>
    </Stack></div>
  );
}
