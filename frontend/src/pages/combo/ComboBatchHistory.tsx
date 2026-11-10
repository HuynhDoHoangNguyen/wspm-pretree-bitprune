import React, { useEffect, useState } from 'react';
import { Alert, Badge, Button, Group, MediaQuery, Paper, Skeleton, Stack, Table, Text, Title } from '@mantine/core';
import { BatchPage, ComboClient } from './ComboClient';
import { MiningEmptyState, useMiningAdminStyles } from '../admin/data-mining/MiningAdminUi';

const savedTime = (value: string) => {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'medium',
  }).format(date);
};
export default function ComboBatchHistory({ client, openBatch, selectedPage, onPageChange }: {
  client: ComboClient; openBatch: (id: string) => void; selectedPage?: number; onPageChange?: (page: number) => void;
}) {
  const { classes } = useMiningAdminStyles();
  const [localPage, setLocalPage] = useState(0), [revision, setRevision] = useState(0);
  const page = selectedPage ?? localPage;
  const [data, setData] = useState<BatchPage | null>(null), [error, setError] = useState('');
  useEffect(() => {
    const controller = new AbortController(); let active = true;
    setData(null); setError('');
    const fetch = client.batches;
    if (!fetch) { setError('Dịch vụ lịch sử batch chưa sẵn sàng.'); return () => controller.abort(); }
    fetch(page, 20, { signal: controller.signal }).then(result => {
      if (!active) return;
      const last = BigInt(result.total) === BigInt(0) ? 0 : Number((BigInt(result.total) - BigInt(1)) / BigInt(20));
      if (page > last) { if (onPageChange) onPageChange(last); else setLocalPage(last); return; }
      setData(result);
    }).catch(() => { if (active) setError('Không tải được lịch sử batch. Kiểm tra kết nối hoặc đăng nhập rồi thử lại.'); });
    return () => { active = false; controller.abort(); };
  }, [client, page, revision, onPageChange]);
  function movePage(next: number) { if (onPageChange) onPageChange(next); else setLocalPage(next); }
  return <Paper withBorder radius="md" p="xl"><Stack spacing="lg">
    <Group position="apart"><div><Title order={3}>Lịch sử lưu DRAFT</Title>
      <Text size="sm" color="dimmed" mt={4}>Mới nhất trước · 20 lần lưu mỗi trang · Thời gian UTC+07</Text></div>
    <Button variant="light" onClick={() => setRevision(v => v + 1)}>Làm mới lịch sử</Button></Group>
    {error ? <Alert color="red" role="alert">{error}<Button variant="subtle" onClick={() => setRevision(v => v + 1)}>Thử lại</Button></Alert>
      : !data ? <Stack role="status" aria-label="Đang tải lịch sử batch">{[1, 2, 3].map(n => <Skeleton key={n} height={72}/>)}</Stack>
        : data.items.length === 0 ? <MiningEmptyState title="Chưa có lần lưu Combo nào">Tạo đề xuất và lưu DRAFT để bắt đầu lịch sử batch.</MiningEmptyState>
          : <><MediaQuery smallerThan="sm" styles={{ display: 'none' }}><div className={classes.scroll} role="region" aria-label="Lịch sử batch" tabIndex={0}>
            <Table highlightOnHover className={classes.table}><thead><tr><th>Lần lưu DRAFT</th><th>Đề xuất</th><th>Tiến độ duyệt</th><th/></tr></thead>
              <tbody>{data.items.map(batch => <tr key={batch.batchId}>
                <td><Text weight={600}>{savedTime(batch.createdAt)}</Text>
                  <Text size="xs" color="dimmed" className={classes.wrap} mt={4}>{batch.batchId}</Text>
                  <Text size="xs" color="dimmed" className={classes.wrap}>Run: {batch.runId}</Text>
                  <Text size="xs">Top-K gợi ý cho khách: {batch.topK ?? '—'}</Text></td>
                <td><Text weight={700} size="lg">{batch.comboCount}</Text><Text size="xs" color="dimmed">Combo trong batch</Text></td>
                <td><Stack spacing={5} align="flex-start"><Badge color="gray" variant="light">{batch.draftCount} bản nháp</Badge>
                  <Text size="xs" color="dimmed">{batch.approvedCount} đã duyệt · {batch.rejectedCount} từ chối</Text></Stack></td>
                <td><Button variant="light" onClick={() => openBatch(batch.batchId)} aria-label={'Mở batch ' + batch.batchId}>Xem đề xuất →</Button></td>
              </tr>)}</tbody></Table>
          </div></MediaQuery>
          <MediaQuery largerThan="sm" styles={{ display: 'none' }}><Stack spacing="sm">
            {data.items.map(batch => <Paper key={batch.batchId} withBorder radius="md" p="md">
              <Group position="apart"><Text weight={600}>{savedTime(batch.createdAt)}</Text><Badge>{batch.comboCount} Combo</Badge></Group>
              <Text size="xs" color="dimmed" mt="xs" className={classes.wrap}>{batch.batchId}</Text>
              <Text size="xs" color="dimmed" className={classes.wrap}>Run: {batch.runId}</Text>
              <Text size="xs">Top-K gợi ý cho khách: {batch.topK ?? '—'}</Text>
              <Text size="sm" mt="sm">{batch.draftCount} bản nháp · {batch.approvedCount} đã duyệt · {batch.rejectedCount} từ chối</Text>
              <Button fullWidth variant="light" mt="md" onClick={() => openBatch(batch.batchId)} aria-label={'Mở batch ' + batch.batchId}>Xem đề xuất →</Button>
            </Paper>)}
          </Stack></MediaQuery></>}
    {data && <Group position="apart" className={classes.footer}><Text size="sm" color="dimmed">{data.total} batch · Trang {page + 1}</Text>
      <Group spacing="xs"><Button variant="default" disabled={page === 0} onClick={() => movePage(page - 1)}>Trang trước</Button>
        <Button variant="default" disabled={BigInt(page + 1) * BigInt(20) >= BigInt(data.total)} onClick={() => movePage(page + 1)}>Trang sau</Button></Group></Group>}
  </Stack></Paper>;
}
