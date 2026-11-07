import React from 'react';
import { Alert, Button, Group, Stack, Text, Title } from '@mantine/core';
import { Link, useLocation, useParams } from 'react-router-dom';
import { ArrowLeft, Clock, Database, History, Refresh, Settings } from 'tabler-icons-react';
import ManagerPath from 'constants/ManagerPath';
import useTitle from 'hooks/use-title';
import { isRunId } from 'api/admin/data-mining/mining-run-response';
import CopyValue from './CopyValue';
import { useMiningRunStatus } from './useMiningRunStatus';
import MiningRunStatusPanel from './MiningRunStatusPanel';
import MiningRunMetricsPanel from './MiningRunMetricsPanel';
import { runErrorMessage } from './mining-run-lifecycle';
import { StateBadge, StoragePanel } from './WorkspacePanels';
import useWorkspaceStyles from './workspace.styles';
import useDetailStyles from './mining-run-detail.styles';

export default function MiningRunDetailPage() {
  useTitle('Chi tiết lần chạy khai phá');
  const { runId = '' } = useParams();
  const operation = useMiningRunStatus(runId);
  const location = useLocation();
  const back = (location.state as { returnTo?: string } | null)?.returnTo;
  const { classes } = useWorkspaceStyles();
  const { classes: detail } = useDetailStyles();
  const valid = isRunId(runId);
  return <Stack mb={30} spacing="lg" className={classes.root} aria-live="polite">
    <nav aria-label="Điều hướng lần chạy" className={detail.navigation}>
      <Button component={Link} to={back?.startsWith('/admin/mining-runs?') ? back : ManagerPath.MINING_RUNS + '?tab=history'} variant="subtle" leftIcon={<ArrowLeft size={17}/>}>Lịch sử run</Button>
      <Button component={Link} to={ManagerPath.MINING_RUNS} variant="default" leftIcon={<Settings size={17}/>}>Về cấu hình khai phá</Button>
    </nav>
    <section className={detail.header} aria-label="Tổng quan lần chạy"><Stack spacing="lg">
      <div><Text color="blue" weight={700} style={{ fontSize: 12, letterSpacing: '1.3px', marginBottom: 10 }}>THEO DÕI KHAI PHÁ</Text>
        <Group position="apart" align="center"><Title order={2} className={detail.title}>Chi tiết lần chạy khai phá</Title>
          {operation.report && <StateBadge value={operation.report.status}/>}</Group>
        <Text color="dimmed" size="sm" mt={8}>Theo dõi trạng thái, cấu hình và các số đo của lần chạy trong cùng một nơi.</Text>
      </div>
      <div className={detail.metadata}>
        <div className={detail.tile}><Text size="xs" color="dimmed" weight={600} mb={8}>MÃ LẦN CHẠY</Text>
          {valid ? <CopyValue value={window.location.origin + ManagerPath.MINING_RUNS + '/' + runId} displayValue={runId} buttonText="Sao chép liên kết" label="link lần chạy"/>
            : <Text size="sm">Run ID chưa hợp lệ.</Text>}
        </div>
        <div className={detail.tile}><Group spacing={8} mb={8}><Clock size={16}/><Text size="xs" color="dimmed" weight={600}>CẬP NHẬT GẦN NHẤT</Text></Group>
          {operation.lastReadAt ? <Text size="sm"><time aria-label="Thời điểm cập nhật" dateTime={operation.lastReadAt} title={'UTC: ' + operation.lastReadAt}>
            {new Date(operation.lastReadAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour12: false })}</time> <Text component="span" size="xs" color="dimmed">(UTC+07)</Text></Text>
            : <Text size="sm" color="dimmed">Chưa có trạng thái được xác nhận.</Text>}
          <Button mt="sm" size="xs" variant="light" leftIcon={<Refresh size={15}/>} aria-label="Làm mới trạng thái — Đọc lại / Tiếp tục theo dõi"
            loading={operation.busy} disabled={operation.busy || operation.hidden || !valid} onClick={() => void operation.read()}>Làm mới trạng thái</Button>
          {operation.busy && <Text role="status" size="xs" mt={6}>Đang đọc trạng thái…</Text>}
        </div>
      </div>
    </Stack></section>
    {operation.error && <Alert role="alert" color="red" title="Chưa thể đọc trạng thái"><Text>{runErrorMessage(operation.error)}</Text>
      {operation.error.requestId && <Text>Mã yêu cầu: {operation.error.requestId}</Text>}</Alert>}
    {(operation.paused || operation.hidden) && <Text role="status">Theo dõi đã tạm dừng. Thông tin bên dưới là lần xác nhận gần nhất; thao tác này không hủy run.</Text>}
    {operation.report && <><section className={detail.related} aria-label="Dữ liệu liên quan">
      <div className={detail.relatedLabel}><span className={detail.icon}><Database size={21}/></span><div>
        <Text weight={600}>Dữ liệu liên quan</Text><Text size="xs" color="dimmed">Snapshot {operation.report.snapshotId.slice(0, 8)}</Text>
      </div></div>
      <div className={detail.actions}><Button component={Link} variant="light" leftIcon={<Database size={16}/>} to={'/admin/datasets/' + operation.report.snapshotId}>Dataset nguồn</Button>
        <Button component={Link} variant="default" leftIcon={<History size={16}/>} to={ManagerPath.MINING_RUNS + '?tab=history&snapshotId=' + operation.report.snapshotId}>Các run cùng dataset</Button></div>
    </section>
    <div className={classes.executionGrid}><MiningRunStatusPanel report={operation.report}/><MiningRunMetricsPanel report={operation.report}/></div>
    <StoragePanel snapshotId={operation.report.snapshotId} historical/>
    <Text size="sm" color="dimmed">Metadata run được lưu trong DB. Patterns đầy đủ chưa có bước lưu bền vững; metrics hiển thị theo trạng thái đã xác nhận.</Text></>}
  </Stack>;
}
