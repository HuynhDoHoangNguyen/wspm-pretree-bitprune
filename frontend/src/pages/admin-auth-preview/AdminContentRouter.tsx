import React from 'react';
import { Alert, Paper, Text, Title } from '@mantine/core';
import type { AdminSection } from './AdminNavigation.config';
import { adminNavigation } from './AdminNavigation.config';

const Monitoring = React.lazy(() => import('../mining-monitoring/MiningMonitoringPreview'));
const History = React.lazy(() => import('../mining-run/MiningRunHistoryPreview'));
const Comparison = React.lazy(() => import('../mining-comparison/MiningComparisonPreview'));

export default function AdminContentRouter({ section }: { section: AdminSection }) {
  let content: React.ReactNode;
  if (section === 'mining-monitoring') content = <Monitoring embedded/>;
  else if (section === 'mining-runs') content = <History embedded/>;
  else if (section === 'mining-comparison') content = <Comparison embedded/>;
  else {
    const item = adminNavigation.find(entry => entry.id === section) || adminNavigation[0];
    content = <Paper withBorder radius="lg" p="xl">
      <Title order={1}>{item.label}</Title><Text color="dimmed" mt="sm">{item.useCase}</Text>
      <Alert color="blue" mt="lg">Chức năng đang chờ tích hợp từ thành viên phụ trách.</Alert>
      <Text size="sm" mt="md">Điểm tích hợp: AdminContentRouter / {item.id}. Đường dẫn production đề xuất {item.productionPath} chưa phải contract đã xác nhận.</Text>
    </Paper>;
  }
  return <React.Suspense fallback={<Text role="status">Đang tải nội dung chức năng…</Text>}>{content}</React.Suspense>;
}
