import React from 'react';
import { Stack, Text, Title } from '@mantine/core';
import { useSearchParams } from 'react-router-dom';
import useTitle from 'hooks/use-title';
import MiningConfigForm from './MiningConfigForm';

export default function MiningRunsPage() {
  useTitle('Lần chạy khai phá');
  const [params] = useSearchParams();
  const values = params.getAll('snapshotId');
  const snapshotId = values.length === 1 ? values[0] : '';
  return (
    <Stack mb={30}><Title order={2}>Lần chạy khai phá</Title>
      <Text>Chọn dataset, xem ngưỡng và kiểm tra cấu hình trước khi tạo lần chạy.</Text>
      <MiningConfigForm key={params.toString()} initialSnapshotId={snapshotId}/>
    </Stack>
  );
}
