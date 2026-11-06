import React, { useEffect, useRef, useState } from 'react';
import { Alert, Badge, Button, Group, Loader, Paper, Stack, Table, Text, Title } from '@mantine/core';
import { useDocumentTitle } from '@mantine/hooks';
import { Link } from 'react-router-dom';
import type { MiningRunDetail, MiningRunPage, MiningRunParams, MiningRunSummary } from '../mining-run/MiningRun.types';
import type { MiningMonitoringProps, MiningMonitoringSource } from './MiningMonitoring.types';
import { elapsedMs, metric, safeExecutionError, statusPresentation, timestampText } from './MiningMonitoring.utils';
import './MiningMonitoring.css';

const listParams: MiningRunParams = { page: 0, size: 20, algorithm: '', status: '' };

function StatusBadge({ run }: { run: MiningRunSummary }) {
  const status = statusPresentation[run.status];
  return <Badge color={status.color}>{run.status} · {status.label}</Badge>;
}

export default function MiningMonitoring({ source, observedAt, historyHref = '/admin/mining-runs', manualRefresh = false }: MiningMonitoringProps) {
  useDocumentTitle('Giám sát quá trình chạy Mining – Admin');
  const [attempt, setAttempt] = useState(0);
  const loading = useRef(true);
  const [state, setState] = useState<{ status: 'loading' | 'error' | 'success'; data: MiningRunPage | null }>({ status: 'loading', data: null });
  useEffect(() => {
    const controller = new AbortController();
    loading.current = true;
    setState({ status: 'loading', data: null });
    Promise.resolve().then(() => source.list(listParams, controller.signal)).then(data => {
      if (!controller.signal.aborted) setState({ status: 'success', data });
    }).catch(() => {
      if (!controller.signal.aborted) setState({ status: 'error', data: null });
    }).finally(() => { if (!controller.signal.aborted) loading.current = false; });
    return () => controller.abort();
  }, [source, attempt]);
  function refresh() {
    if (loading.current) return;
    loading.current = true;
    setState({ status: 'loading', data: null });
    setAttempt(value => value + 1);
  }
  return <Stack spacing="xl">
    <Group position="apart" align="flex-start">
      <div><Text color="blue" weight={600} size="sm">ADMIN · MINING MONITORING</Text>
        <Title order={1}>Giám sát quá trình chạy Mining</Title>
        <Text color="dimmed" mt="xs">Theo dõi RUN đã ghi nhận · Không khởi chạy, hủy hoặc chạy lại Mining</Text>
      </div>
      <Stack spacing={5}>
        {manualRefresh && <Button color="blue" disabled={state.status === 'loading'} onClick={refresh}>Làm mới</Button>}
        {historyHref ? <Button component={Link} to={historyHref} variant="light">Xem lịch sử RUN</Button>
          : <Button disabled variant="light" aria-describedby="monitoring-history-hint">Xem lịch sử RUN</Button>}
        {!historyHref && <Text id="monitoring-history-hint" size="xs" color="dimmed">
          Bật REACT_APP_ENABLE_MINING_PREVIEW=true và khởi động lại frontend để xem lịch sử Preview.
        </Text>}
      </Stack>
    </Group>
    {manualRefresh && <Text size="sm" color="dimmed">Dữ liệu tại lần tải gần nhất · Nhấn Làm mới để cập nhật, không tự động làm mới.</Text>}
    {state.status === 'loading' && <Text role="status">Đang tải danh sách RUN…</Text>}
    {state.status === 'error' && <Alert color="red" title="Không thể tải danh sách RUN">
      <Text mb="sm">Vui lòng thử lại.</Text><Button onClick={refresh}>Thử lại</Button>
    </Alert>}
    {state.status === 'success' && state.data && (state.data.items.length
      ? <RunList runs={state.data.items} source={source} observedAt={observedAt} total={state.data.totalElements}/>
      : <Alert color="blue">Chưa có RUN để theo dõi.</Alert>)}
  </Stack>;
}

function RunList({ runs, source, observedAt, total }: {
  runs: MiningRunSummary[]; source: MiningMonitoringSource; observedAt: number; total: number;
}) {
  const [selectedId, setSelectedId] = useState<string | null>(runs[0]?.runId || null);
  const [detailRequest, setDetailRequest] = useState(0);
  const panel = useRef<HTMLDivElement>(null);
  const selectedButton = useRef<HTMLButtonElement | null>(null);
  useEffect(() => {
    if (selectedId && detailRequest > 0) {
      panel.current?.focus({ preventScroll: true });
      panel.current?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
    }
  }, [selectedId, detailRequest]);
  return <Stack spacing="xl">
    <Paper withBorder radius="lg" p="lg">
      <Title order={2} mb="sm">Danh sách RUN theo dõi</Title>
      <Text size="xs" color="dimmed" mb="md">Hiển thị {runs.length} / {total} RUN gần đây. Xem lịch sử RUN để tra cứu đầy đủ.</Text>
      <div className="mining-monitoring-table-scroll">
        <Table aria-label="Danh sách RUN theo dõi" className="mining-monitoring-table" highlightOnHover>
          <thead><tr><th>RUN ID</th><th>Thuật toán</th><th>Dataset</th><th>Bắt đầu (UTC)</th><th>Thời gian theo timestamp</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
          <tbody>{runs.map(run => <tr key={run.runId} aria-selected={selectedId === run.runId}>
            <td>{run.runId}</td><td>{run.algorithm}</td><td>{run.snapshotId || run.datasetSource}</td>
            <td>{timestampText(run.startedAt)}</td><td>{metric(elapsedMs(run, observedAt), 'ms')}</td>
            <td><StatusBadge run={run}/></td>
            <td><Button size="xs" variant={selectedId === run.runId ? 'filled' : 'light'}
              aria-label={`Xem chi tiết ${run.runId}`} aria-pressed={selectedId === run.runId}
              aria-expanded={selectedId === run.runId} aria-controls={selectedId === run.runId ? 'monitoring-run-detail-panel' : undefined}
              onClick={(event: React.MouseEvent<HTMLButtonElement>) => {
                selectedButton.current = event.currentTarget;
                setSelectedId(run.runId);
                setDetailRequest(value => value + 1);
              }}>{selectedId === run.runId ? 'Đang xem chi tiết' : 'Xem chi tiết'}</Button></td>
          </tr>)}</tbody>
        </Table>
      </div>
    </Paper>
    {selectedId && <div ref={panel} id="monitoring-run-detail-panel" role="region" aria-label="Panel chi tiết RUN" tabIndex={-1} className="mining-monitoring-detail-panel">
      <Group position="apart" mb="md">
        <div><Title order={2}>Chi tiết RUN đang theo dõi</Title><Text color="blue">RUN: {selectedId}</Text></div>
        <Button variant="light" onClick={() => {
          setSelectedId(null);
          selectedButton.current?.focus();
        }}>Đóng chi tiết</Button>
      </Group>
      <RunDetail key={selectedId} runId={selectedId} source={source} observedAt={observedAt}/>
    </div>}
  </Stack>;
}

function RunDetail({ runId, source, observedAt }: { runId: string; source: MiningMonitoringSource; observedAt: number }) {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<{ status: 'loading' | 'error' | 'success'; run: MiningRunDetail | null }>({ status: 'loading', run: null });
  useEffect(() => {
    const controller = new AbortController();
    setState({ status: 'loading', run: null });
    Promise.resolve().then(() => source.detail(runId, controller.signal)).then(run => {
      if (!controller.signal.aborted) {
        if (run.runId !== runId) throw new Error('Unexpected RUN');
        setState({ status: 'success', run });
      }
    }).catch(() => {
      if (!controller.signal.aborted) setState({ status: 'error', run: null });
    });
    return () => controller.abort();
  }, [runId, source, attempt]);
  if (state.status === 'loading') return <Text role="status">Đang tải chi tiết RUN…</Text>;
  if (state.status === 'error') return <Alert color="red" title="Không thể tải chi tiết RUN">
    <Button onClick={() => setAttempt(value => value + 1)}>Thử lại chi tiết</Button>
  </Alert>;
  if (!state.run) return null;
  const run = state.run;
  const status = statusPresentation[run.status];
  const fields: [string, string][] = [
    ['RUN ID', run.runId], ['Thuật toán', run.algorithm], ['Dataset', `${run.datasetSource} · ${run.snapshotId || 'Chưa có dữ liệu'}`],
    ['Bắt đầu (UTC)', timestampText(run.startedAt)], ['Kết thúc (UTC)', timestampText(run.finishedAt)],
    ['Thời gian theo timestamp', metric(elapsedMs(run, observedAt), 'ms')],
    ['Minsup cấu hình', `${run.minsupMode} / ${metric(run.minsupConfigured)}`], ['Ngưỡng hiệu lực', metric(run.effectiveThreshold)],
    ['Minsup tương đối (fraction)', metric(run.minsupRelativeFraction)],
    ['MaxW / MinW / MeanW', `${metric(run.maxW)} / ${metric(run.minW)} / ${metric(run.meanW)}`],
  ];
  return <section aria-label="Chi tiết RUN đang theo dõi" className="mining-monitoring-detail">
    <Paper withBorder radius="lg" p="xl">
      <Title order={2} mb="md">Thông tin RUN</Title><StatusBadge run={run}/>
      <dl className="mining-monitoring-fields">{fields.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
    </Paper>
    <Paper withBorder radius="lg" p="xl">
      <Title order={2} mb="md">Trạng thái Mining</Title>
      <Text size="lg" weight={600} color={status.color}>{status.label}</Text>
      {run.status === 'RUNNING' && <Stack spacing="sm" mt="md">
        <Group><Loader size="sm" aria-label="Mining đang chạy"/><Text>RUNNING — đang thực thi</Text></Group>
        <Text size="sm" color="dimmed">Chưa có dữ liệu phần trăm tiến độ. Thời gian trôi qua không biểu thị phần trăm hoàn thành.</Text>
      </Stack>}
      {run.status === 'QUEUED' && <Text mt="md">RUN chưa bắt đầu; chưa có thời gian thực thi.</Text>}
      {run.status === 'SUCCEEDED' && <Stack spacing="sm" mt="md" aria-label="Kết quả RUN thành công">
        <Text>Tổng thực thi cuối cùng: {metric(run.totalExecutionMs, 'ms')}</Text>
        <Text>Số pattern: {metric(run.patternCount)}</Text>
        <Text>Số candidate: {metric(run.candidateCount)}</Text>
        <Text>Đỉnh JVM heap lấy mẫu: {metric(run.peakSampledJvmHeapBytes, 'bytes')}</Text>
        <Text size="xs" color="dimmed">Metric tổng thực thi khác thời gian theo timestamp; heap lấy mẫu không phải RAM phần cứng chính xác.</Text>
      </Stack>}
      {run.status === 'FAILED' && <Alert color="red" title="RUN thất bại" mt="md">{safeExecutionError(run.errorMessage)}</Alert>}
      {run.status === 'LEGACY_UNVERIFIED' && <Text mt="md">Chưa có đủ dữ liệu để xác nhận kết quả thực thi.</Text>}
    </Paper>
  </section>;
}
