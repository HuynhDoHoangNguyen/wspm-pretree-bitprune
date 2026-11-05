import React from 'react';
import { SimpleGrid, Stack, Text, Title } from '@mantine/core';
import useTitle from 'hooks/use-title';
import DatasetExportForm from './DatasetExportForm';
import DatasetUploadForm from './DatasetUploadForm';

export default function DatasetsPage() {
  useTitle('Dữ liệu khai phá');
  return (
    <Stack spacing="lg" mb={30}>
      <Stack spacing={4}>
        <Text size="sm" color="dimmed">Dữ liệu &amp; Khai phá</Text>
        <Title order={2}>Dữ liệu khai phá</Title>
        <Text color="dimmed">Xuất lịch sử đơn hàng và lưu CSV thành snapshot để chuẩn bị khai phá mẫu sản phẩm.</Text>
      </Stack>
      <SimpleGrid cols={2} breakpoints={[{ maxWidth: 'sm', cols: 1 }]}>
        <DatasetExportForm/>
        <DatasetUploadForm/>
      </SimpleGrid>
    </Stack>
  );
}
