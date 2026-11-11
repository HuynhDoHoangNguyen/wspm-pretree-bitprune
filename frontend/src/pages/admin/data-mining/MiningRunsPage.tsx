import React, { useState } from 'react';
import { Button, Group, Stack, TextInput } from '@mantine/core';
import { useNavigate, useSearchParams } from 'react-router-dom';
import ManagerPath from 'constants/ManagerPath';
import { isRunId } from 'api/admin/data-mining/mining-run-response';
import useTitle from 'hooks/use-title';
import AdminPageHeader from './AdminPageHeader';
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
      <AdminPageHeader title="Lần chạy khai phá" description="Cấu hình lần chạy mới hoặc tra cứu lịch sử khai phá."
        action={<Button onClick={() => { const next = new URLSearchParams(params); next.set('tab', history ? 'create' : 'history'); setParams(next); }}>{history ? 'Tạo lần chạy' : 'Lịch sử lần chạy'}</Button>}/>
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
