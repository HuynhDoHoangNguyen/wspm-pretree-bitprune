import React from 'react';
import { Anchor, Paper, Stack, Text } from '@mantine/core';
import { Link } from 'react-router-dom';
import { datasetApi } from 'api/admin/data-mining';
import { isSnapshotId } from 'api/admin/data-mining/dataset-response';
import { useWorkspaceRead } from './useWorkspaceRead';
import { ReadError, StateBadge, StoragePanel } from './WorkspacePanels';

export default function SelectedDatasetPanel({ snapshotId }: { snapshotId: string }) {
  const valid = isSnapshotId(snapshotId);
  const read = useWorkspaceRead(valid ? 'dataset:' + snapshotId : '', signal => datasetApi.detail(snapshotId, signal));
  return <Stack spacing="sm">
    <Paper withBorder radius="md" p="md"><Text size="sm" color="dimmed">DỮ LIỆU ĐÃ CHỌN</Text>
      {!valid && <><Text>Chọn một snapshot đã chuyển đổi trong thư viện dữ liệu.</Text><Anchor href="/admin/datasets">Mở thư viện dữ liệu</Anchor></>}
      {read.loading && <Text>Đang tải dataset…</Text>}{read.error && <ReadError retry={read.refresh} error={read.error}/>}
      {read.value && <Stack spacing="xs"><Text weight={600}>{read.value.sourceName || 'Snapshot chưa có tên'}</Text>
        <Text size="sm">{read.value.sequenceCount ?? 'Chưa có'} sequence · {read.value.sourceType}</Text>
        <StateBadge value={read.value.conversionStatus || read.value.validationStatus || 'LEGACY_UNVERIFIED'}/>
        <Anchor component={Link} to={'/admin/datasets/' + snapshotId}>Chi tiết dataset và tệp nguồn</Anchor></Stack>}
    </Paper>
    {valid && <StoragePanel snapshotId={snapshotId}/>}
  </Stack>;
}
