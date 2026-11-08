import React, { useState } from 'react';
import { Button, Group, Paper, Stack, Text, TextInput, Title } from '@mantine/core';
import { useNavigate, useSearchParams } from 'react-router-dom';
import ManagerPath from 'constants/ManagerPath';
import { isRunId } from 'api/admin/data-mining/mining-run-response';
import useTitle from 'hooks/use-title';
import MiningConfigForm from './MiningConfigForm';
import WorkspaceList from './WorkspaceList';
import { CapacityPanel } from './WorkspacePanels';
import useWorkspaceStyles from './workspace.styles';

export default function MiningRunsPage() {
  useTitle('Lần chạy khai phá');
  const [params, setParams] = useSearchParams();
  const { classes } = useWorkspaceStyles();
  const history = params.get('tab') === 'history';
  const navigate = useNavigate();
  const [runId, setRunId] = useState(''), [attempted, setAttempted] = useState(false);
  const values = params.getAll('snapshotId');
  const snapshotId = values.length === 1 ? values[0] : '';
  return (
    <Stack mb={30} className={classes.root}>
      <Paper p="lg" radius="md" className={classes.hero}><Text size="sm" weight={700} color="blue">KHÔNG GIAN KHAI PHÁ</Text><Title order={2}>Lần chạy khai phá</Title>
        <Text mt="xs">Cấu hình một lần chạy mới hoặc tìm lại lịch sử và thông số đã lưu.</Text></Paper>
      <div role="tablist" aria-label="Không gian khai phá" className={classes.tabs}>
        {[['create', 'Tạo lần chạy'], ['history', 'Lịch sử']].map(([tab, label]) => <button key={tab} role="tab" aria-selected={history === (tab === 'history')} className={classes.tab}
          onClick={() => { const next = new URLSearchParams(); next.set('tab', tab); if (snapshotId) next.set('snapshotId', snapshotId); setParams(next); }}>{label}</button>)}
      </div>
      {history ? <WorkspaceList runs/> : <><CapacityPanel/>
        <MiningConfigForm key={params.toString()} initialSnapshotId={snapshotId} onAccepted={result => navigate(ManagerPath.MINING_RUNS + '/' + result.runId)}/>
        <details><summary>Mở trực tiếp bằng Run ID</summary>
          <form aria-label="Mở lần chạy đã biết" onSubmit={event => { event.preventDefault(); setAttempted(true); if (isRunId(runId)) navigate(ManagerPath.MINING_RUNS + '/' + runId); }}>
            <Group align="end"><TextInput label="Run ID" value={runId} error={attempted && !isRunId(runId) ? 'Nhập UUID run chuẩn, chữ thường.' : undefined}
              onChange={event => setRunId(event.currentTarget.value)}/><Button type="submit" variant="outline">Mở run theo ID</Button></Group>
          </form>
        </details></>}
    </Stack>
  );
}
