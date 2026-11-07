import React from 'react';
import { Alert, Anchor, Badge, Button, Group, Paper, Stack, Text, Title } from '@mantine/core';
import { Link } from 'react-router-dom';
import { workspaceApi, WdmApiError } from 'api/admin/data-mining';
import { useWorkspaceRead } from './useWorkspaceRead';
import CopyValue from './CopyValue';

export const shortId = (id: string) => id.slice(0, 8);
export const localTime = (value: string | null) => value ? new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour12: false }) : 'Chưa có';
export const stateLabel: Record<string, string> = { QUEUED: 'Đang chờ', RUNNING: 'Đang xử lý', SUCCEEDED: 'Hoàn tất', FAILED: 'Thất bại', LEGACY_UNVERIFIED: 'Dữ liệu cũ chưa xác minh',
  PENDING: 'Chờ kiểm tra', VALID: 'Đã kiểm tra', INVALID: 'Cần xử lý dữ liệu', NOT_STARTED: 'Chưa chuyển đổi', RESERVED: 'Đang tiếp nhận', ACTIVATING: 'Đang tiếp nhận', SUBMITTED: 'Đang chờ', COMPLETED: 'Kết thúc · còn giữ kết quả', CLOSED: 'Đã đóng · còn giữ slot' };
export function StateBadge({ value }: { value: string }) {
  return <Badge color={['SUCCEEDED', 'VALID', 'COMPLETED'].includes(value) ? 'teal' : ['FAILED', 'INVALID'].includes(value) ? 'red' : 'blue'}>{stateLabel[value] || value}</Badge>;
}
export function ReadError({ retry, error }: { retry: () => void; error?: WdmApiError | null }) {
  const message = error?.status === 401 ? 'Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.'
    : error?.status === 403 ? 'Tài khoản chưa có quyền xem thông tin này.'
      : error?.status === 404 ? 'Không tìm thấy API hoặc dữ liệu. Kiểm tra backend đang chạy đúng phiên bản.'
        : error?.kind === 'response' ? 'Phản hồi của máy chủ chưa đúng định dạng được hỗ trợ.'
          : error?.status && error.status >= 500 ? 'Máy chủ chưa xử lý được yêu cầu. Đây là lỗi phía backend; hãy thử lại hoặc đối chiếu mã yêu cầu.'
            : 'Chưa kết nối được tới máy chủ hoặc yêu cầu chưa hoàn tất. Hãy thử lại.';
  return <Alert color="red" role="alert" title="Chưa tải được thông tin"><Text size="sm">{message}</Text>
    {error?.status && <Text size="xs">HTTP {error.status}</Text>}{error?.requestId && <Text size="xs">Mã yêu cầu: {error.requestId}</Text>}
    <Button variant="subtle" onClick={retry}>Thử lại</Button></Alert>;
}
export function CapacityPanel() {
  const read = useWorkspaceRead('capacity', signal => workspaceApi.capacity(signal), 10000), info = read.value, s = info?.snapshot;
  return <Paper withBorder radius="md" p="lg" component="section" aria-label="Khả năng tiếp nhận run"><Stack spacing="sm">
    <Group position="apart"><Title order={3}>Khả năng tiếp nhận run</Title><Button variant="subtle" size="xs" onClick={read.refresh} disabled={read.loading}>Làm mới</Button></Group>
    {read.loading && <Text role="status">Đang cập nhật capacity…</Text>}{read.error && <ReadError retry={read.refresh} error={read.error}/>}
    {info && (!info.enabled ? <Text>Thực thi đang tắt trên backend hiện tại.</Text> : s && <>
      <Group spacing="xl"><Text>Đang sử dụng <strong>{s.used} / {s.capacity}</strong> slot</Text><Badge color={s.available ? 'teal' : 'orange'}>Còn {s.available} slot</Badge></Group>
      {s.closing && <Alert color="yellow">Backend đang đóng, không tiếp nhận thêm run.</Alert>}
      {!s.available && <Alert color="yellow">Đã đầy slot. Run đã kết thúc vẫn có thể giữ slot trong bộ nhớ; hệ thống chưa có bước bàn giao kết quả tự động.</Alert>}
      {!!s.entries.length && <details><summary>Run đang chiếm slot ({s.entries.length})</summary><Stack spacing="xs" mt="sm">{s.entries.map(e => <Group key={e.runId}>
        {['RESERVED', 'ACTIVATING'].includes(e.state) ? <Text size="sm">{shortId(e.runId)} · chưa xác nhận tiếp nhận</Text>
          : <Anchor component={Link} to={'/admin/mining-runs/' + e.runId}>{shortId(e.runId)}</Anchor>}
        <StateBadge value={e.state}/>{e.state === 'FAILED' && <Text size="xs">Còn giữ slot</Text>}</Group>)}</Stack></details>}
    </>)}
    {info && <Text size="xs" color="dimmed">Instance backend hiện tại · {localTime(info.observedAt)} (UTC+07). Đây là trạng thái lúc đọc, không phải số lần được lưu DB.</Text>}
  </Stack></Paper>;
}
export function StoragePanel({ snapshotId, historical = false }: { snapshotId: string; historical?: boolean }) {
  const read = useWorkspaceRead('storage:' + snapshotId, signal => workspaceApi.storage(snapshotId, signal));
  return <Paper withBorder radius="md" p="md"><details><summary>Tệp dữ liệu trên backend</summary><Stack mt="md" spacing="sm">
    {historical && <Alert color="yellow">Chưa xác minh vị trí tệp lịch sử của run này. Bên dưới là publication hiện tại của snapshot nguồn.</Alert>}
    <Text size="sm">File trên máy chạy backend được dùng để nạp dữ liệu; bản tải xuống là bản sao. Đường dẫn là metadata, chưa xác nhận file còn tồn tại hoặc checksum còn đúng.</Text>
    {read.loading && <Text>Đang tải vị trí tệp…</Text>}{read.error && <ReadError retry={read.refresh} error={read.error}/>}
    {read.value && <><CopyValue label="Thư mục lưu trữ backend" value={read.value.root}/>
      {!read.value.files.length && <Text>Chưa có đường dẫn tệp hợp lệ để hiển thị.</Text>}
      {read.value.files.map(f => <Paper key={f.kind} p="sm" withBorder style={{ overflowWrap: 'anywhere' }}>
        <Text weight={600}>{f.kind === 'raw' ? 'CSV gốc' : f.kind === 'sequence' ? 'SPMF' : 'Item mapping'} · {f.filename}</Text>
        <Text size="sm">{f.reference}</Text><CopyValue label={'Vị trí ' + f.filename} value={f.absolutePath}/>
        {f.sha256 && <details><summary>Checksum đã lưu</summary><Text size="xs">{f.sha256}</Text></details>}
      </Paper>)}</>}
  </Stack></details></Paper>;
}
