import React, { useState } from 'react';
import { Button, Group, Paper, Select, Stack, Text, TextInput, Title } from '@mantine/core';
import { MiningRequest, MiningRunAccepted } from 'api/admin/data-mining';
import { isSnapshotId } from 'api/admin/data-mining/dataset-response';
import { useMiningConfiguration } from './useMiningConfiguration';
import MiningConfigResult from './MiningConfigResult';
import { useMiningRunCreation } from './useMiningRunCreation';
import MiningRunCreationFeedback from './MiningRunCreationFeedback';

export default function MiningConfigForm({ initialSnapshotId = '' }: { initialSnapshotId?: string }) {
  const [snapshotId, setSnapshotId] = useState(initialSnapshotId);
  const [algorithm, setAlgorithm] = useState<MiningRequest['algorithm']>('WSPM_PRETREE');
  const [mode, setMode] = useState<MiningRequest['minsupMode']>('PERCENT');
  const [value, setValue] = useState('');
  const [attempted, setAttempted] = useState(false);
  const operation = useMiningConfiguration(JSON.stringify([snapshotId, algorithm, mode, value]));
  const [acceptedRun, setAcceptedRun] = useState<MiningRunAccepted | null>(null);
  const creation = useMiningRunCreation(operation.validation, setAcceptedRun);
  const submitting = creation.phase === 'submitting';
  function resetCreation() { creation.reset(); setAcceptedRun(null); operation.invalidate(); }
  const numeric = /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(value) ? Number(value) : NaN;
  const idError = !isSnapshotId(snapshotId) || snapshotId.length !== 36 ? 'Nhập UUID snapshot chuẩn, chữ thường.' : null;
  const valueError = !Number.isFinite(numeric) || numeric < 0 || (mode === 'PERCENT' && numeric > 100)
    ? mode === 'PERCENT' ? 'Nhập số hữu hạn từ 0 đến 100.' : 'Nhập số hữu hạn không âm.' : null;
  function edit(work: () => void) { if (submitting) return; operation.invalidate(); work(); }
  function send(action: 'preview' | 'validate') {
    setAttempted(true);
    if (idError || valueError || submitting) return;
    void operation.run(action, { snapshotId, algorithm, minsupMode: mode, minsupValue: numeric });
  }
  return <Stack>
    <Paper withBorder radius="md" p="lg"><form noValidate aria-label="Cấu hình khai phá" onSubmit={event => { event.preventDefault(); send('preview'); }}>
      <fieldset disabled={submitting} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}><Stack>
        <Title order={3}>Cấu hình khai phá</Title>
        <TextInput label="Snapshot ID" value={snapshotId} required error={attempted ? idError : undefined}
          description="Nhập ID hoặc mở cấu hình từ trang chi tiết snapshot đã chuyển đổi." onChange={event => { const next = event.currentTarget.value; edit(() => setSnapshotId(next)); }}/>
        <Select label="Thuật toán" value={algorithm} data={[{ value: 'WSPM_PRETREE', label: 'WSPM_PreTree' }, { value: 'WSPM_BITPRUNE', label: 'WSPM_BitPrune' }]}
          onChange={next => { if (next === 'WSPM_PRETREE' || next === 'WSPM_BITPRUNE') edit(() => setAlgorithm(next)); }}/>
        <Select label="Mode minsup" value={mode} data={[{ value: 'PERCENT', label: 'Phần trăm (PERCENT)' }, { value: 'ABSOLUTE', label: 'Tuyệt đối (ABSOLUTE)' }]}
          onChange={next => { if (next === 'PERCENT' || next === 'ABSOLUTE') edit(() => setMode(next)); }}/>
        <TextInput label="Giá trị minsup" value={value} required inputMode="decimal" error={attempted ? valueError : undefined}
          description={mode === 'PERCENT' ? '14 nghĩa là 14%. Có thể nhập số thập phân hoặc ký pháp như 1e-6.' : 'Ngưỡng hỗ trợ có trọng số; có thể là số thập phân.'}
          onChange={event => { const next = event.currentTarget.value; edit(() => setValue(next)); }}/>
        <Text size="sm">Ngưỡng zero có thể xem trước nhưng chưa thuộc phạm vi thực thi được hỗ trợ. Mọi ngưỡng hiển thị được lấy từ backend.</Text>
        <Group><Button type="submit" loading={operation.busy} disabled={operation.busy || !operation.allowed}>Xem trước dữ liệu</Button>
          <Button type="button" variant="outline" disabled={operation.busy || !operation.allowed} onClick={() => send('validate')}>Kiểm tra cấu hình</Button></Group>
        <Button type="button" loading={submitting}
          disabled={submitting || operation.busy || !creation.allowed || !operation.validation || creation.phase !== 'ready' || !!idError || !!valueError}
          onClick={() => void creation.create({ snapshotId, algorithm, minsupMode: mode, minsupValue: numeric })}>Tạo lần chạy</Button>
      </Stack></fieldset>
    </form></Paper>
    <MiningRunCreationFeedback phase={creation.phase} submitted={creation.submitted} error={creation.error} onReset={resetCreation}/>
    {creation.phase === 'accepted' && acceptedRun && <Stack>
      <Text role="status">RUN ID: {acceptedRun.runId}</Text>
      <Button type="button" variant="outline" onClick={resetCreation}>Tạo yêu cầu mới</Button>
    </Stack>}
    <MiningConfigResult preview={operation.preview} validation={operation.validation} error={operation.error}/>
  </Stack>;
}
