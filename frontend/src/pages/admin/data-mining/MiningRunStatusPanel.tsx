import React from 'react';
import { Alert, Badge, Paper, Stack, Text, Title } from '@mantine/core';
import { MiningRunStatus } from 'api/admin/data-mining';
import { runFailureMessages } from 'api/admin/data-mining/mining-run-response';
import CopyValue from './CopyValue';
import useWorkspaceStyles from './workspace.styles';

const labels = { QUEUED: 'Đang chờ', RUNNING: 'Đang khai phá', SUCCEEDED: 'Khai phá hoàn tất', FAILED: 'Thất bại', LEGACY_UNVERIFIED: 'Dữ liệu lịch sử chưa xác minh' };
const display = (value: string | number | null) => value === null ? 'Chưa có dữ liệu' : String(value);
export default function MiningRunStatusPanel({ report }: { report: MiningRunStatus }) {
  const c = report.configuration;
  const { classes } = useWorkspaceStyles();
  return <Paper withBorder radius="md" p="lg"><Stack spacing="md">
    <Title order={3}>Trạng thái lần chạy</Title><Badge color={report.status === 'FAILED' ? 'red' : report.status === 'SUCCEEDED' ? 'teal' : 'blue'}>{labels[report.status]}</Badge>
    <CopyValue value={report.runId} label="run ID"/>
    <dl className={classes.summary}><div><dt>Snapshot ID</dt><dd style={{ overflowWrap: 'anywhere' }}>{report.snapshotId}</dd></div><div><dt>Thuật toán</dt><dd>{report.algorithm}</dd></div>
      <div>
        <dt>Mode minsup</dt><dd>{c.minsupMode}</dd><dt>Giá trị đã cấu hình</dt><dd>{display(c.configuredValue)}</dd>
      </div><div>
        <dt>Tỷ lệ đã resolve</dt><dd>{display(c.relativeFraction)}</dd><dt>Ngưỡng hiệu dụng</dt><dd>{display(c.effectiveThreshold)}</dd>
      </div><div>
        <dt>MaxW / MinW / MeanW</dt><dd>{display(c.maxW)} / {display(c.minW)} / {display(c.meanW)}</dd>
      </div></dl>
    <div><Text size="sm" weight={600}>Mốc thời gian (UTC)</Text><dl className={classes.summary}><div><dt>Tạo lúc (UTC)</dt><dd>{report.createdAt}</dd></div><div><dt>Bắt đầu (UTC)</dt><dd>{display(report.startedAt)}</dd></div><div><dt>Kết thúc (UTC)</dt><dd>{display(report.finishedAt)}</dd></div></dl></div>
    {report.status === 'SUCCEEDED' && <Text role="status">{report.patternCount === '0' ? 'Khai phá hoàn tất với 0 patterns.' : 'Khai phá đã hoàn tất.'} Patterns chưa được xác nhận lưu bền vững; chức năng xem kết quả sẽ được bổ sung sau.</Text>}
    {report.status === 'FAILED' && <Alert color="red" role="alert" title="Lần chạy thất bại">{runFailureMessages[report.failure?.code ?? ''] || runFailureMessages.RUN_FAILED}</Alert>}
    {report.status === 'LEGACY_UNVERIFIED' && <Text>Dữ liệu lịch sử chưa được xác minh theo workflow hiện tại.</Text>}
    {['QUEUED', 'RUNNING'].includes(report.status) && <Text role="status">Đang theo dõi trạng thái từ máy chủ…</Text>}
  </Stack></Paper>;
}
