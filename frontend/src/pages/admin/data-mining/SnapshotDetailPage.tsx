import React, { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Alert, Anchor, Button, Paper, Stack, Text, Title } from '@mantine/core';
import { datasetApi, SnapshotDetail, WdmApiError } from 'api/admin/data-mining';
import useTitle from 'hooks/use-title';
import { useDatasetRequest } from './useDatasetRequest';
import { downloadDatasetFile } from './downloadDatasetFile';

function Failure({ error, download = false }: { error: WdmApiError; download?: boolean }) {
  const messages: Record<number, string> = {
    400: 'Snapshot ID không hợp lệ.', 401: 'Phiên đăng nhập không còn hợp lệ.',
    403: 'Tài khoản không có quyền truy cập snapshot.',
    404: download ? 'Không tìm thấy snapshot hoặc tệp gốc. Hãy tải lại chi tiết.' : 'Không tìm thấy snapshot này.',
    503: 'Chưa thể truy cập hoặc xác minh dữ liệu. Vui lòng thử lại sau.',
  };
  return <Alert role="alert" color="red" title={download ? 'Chưa thể tải CSV gốc' : 'Không thể đọc snapshot'}>
    <Text size="sm">{messages[error.status ?? 0] || 'Không thể xác nhận phản hồi. Vui lòng kiểm tra kết nối và thử lại.'}</Text>
    {error.requestId && <Text size="sm">Mã yêu cầu: {error.requestId}</Text>}
  </Alert>;
}

function Field({ label, value }: { label: string; value: string | null }) {
  return <div><dt><Text color="dimmed" size="sm">{label}</Text></dt>
    <dd style={{ margin: 0, overflowWrap: 'anywhere' }}>{value ?? 'Chưa xác định'}</dd></div>;
}

function RawDownload({ detail }: { detail: SnapshotDetail }) {
  const request = useDatasetRequest();
  const [sent, setSent] = useState(false);
  function download() {
    setSent(false);
    void request.run(signal => datasetApi.downloadRaw(detail.snapshotId, signal), result => {
      downloadDatasetFile(result);
      setSent(true);
    });
  }
  return <Stack>
    <Text size="sm">Checksum được ghi khi tiếp nhận. Tệp gốc được kiểm tra lại trước khi tải; nội dung CSV chưa mặc nhiên được xác nhận hợp lệ.</Text>
    {detail.rawAvailability !== 'AVAILABLE' && <Alert role="alert" color="yellow" title="CSV gốc chưa sẵn sàng">
      {detail.rawAvailability === 'MISSING' ? 'Chưa có hoặc không tìm thấy tệp gốc của snapshot.' : 'Chưa thể truy cập tệp gốc hoặc metadata không đủ để xác minh.'}
    </Alert>}
    {request.error && <Failure error={request.error} download/>}
    {sent && <Text role="status" color="teal">Đã gửi CSV gốc để trình duyệt tải xuống.</Text>}
    <Button onClick={download} loading={request.busy} disabled={request.busy || detail.rawAvailability !== 'AVAILABLE'}>Tải CSV gốc</Button>
  </Stack>;
}

export default function SnapshotDetailPage() {
  useTitle('Chi tiết snapshot');
  const id = useParams<{ snapshotId: string }>().snapshotId || '';
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<{ id: string; loading: boolean; detail: SnapshotDetail | null; error: WdmApiError | null }>({ id: '', loading: true, detail: null, error: null });
  useEffect(() => {
    const controller = new AbortController();
    setState({ id, loading: true, detail: null, error: null });
    datasetApi.detail(id, controller.signal).then(detail => {
      if (!controller.signal.aborted) setState({ id, loading: false, detail, error: null });
    }).catch(error => {
      if (!controller.signal.aborted) setState({ id, loading: false, detail: null,
        error: error instanceof WdmApiError ? error : new WdmApiError('response', null, 'INVALID_DETAIL_RESPONSE', 'Invalid response.') });
    });
    return () => controller.abort();
  }, [id, revision]);
  const loading = state.id !== id || state.loading;
  const detail = !loading && state.id === id ? state.detail : null;
  const validations = { PENDING: 'PENDING — Chưa kiểm tra dữ liệu', VALID: 'VALID — Đã đạt kiểm tra', INVALID: 'INVALID — Không hợp lệ', LEGACY_UNVERIFIED: 'Dữ liệu cũ chưa xác minh' };
  return <Stack spacing="lg" mb={30}>
    <Anchor component={Link} to="/admin/datasets">Về trang dữ liệu</Anchor>
    <Title order={2}>Chi tiết snapshot</Title>
    {loading && <Text role="status">Đang tải chi tiết snapshot…</Text>}
    {!loading && state.error && <Failure error={state.error}/>}
    <Button variant="light" onClick={() => setRevision(value => value + 1)} disabled={loading}>Tải lại chi tiết</Button>
    {detail && <Paper withBorder radius="md" p="lg"><Stack spacing="lg">
      <dl style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 240px), 1fr))', gap: 16, margin: 0 }}>
        <Field label="Snapshot ID" value={detail.snapshotId}/>
        <Field label="Tên nguồn" value={detail.sourceName}/>
        <Field label="Loại nguồn" value={detail.sourceType}/>
        <Field label="Trạng thái kiểm tra" value={detail.validationStatus ? validations[detail.validationStatus] : null}/>
        <Field label="Chuyển đổi sequence" value={detail.conversionStatus === 'NOT_STARTED' ? 'Chưa chuyển đổi' : null}/>
        <Field label="Thống kê" value={detail.statisticsStatus === 'NOT_COMPUTED' ? 'Chưa tính' : detail.statisticsStatus === 'COMPUTED' ? 'Đã tính' : null}/>
        <Field label="Số sequence" value={detail.sequenceCount}/>
        <Field label="Dung lượng gốc" value={detail.rawSizeBytes === null ? null : detail.rawSizeBytes + ' byte'}/>
        <Field label="SHA-256 khi tiếp nhận" value={detail.rawSha256}/>
        <Field label="Thời điểm tạo (UTC)" value={detail.createdAt}/>
        <Field label="Từ thời điểm (UTC)" value={detail.fromTime}/>
        <Field label="Đến thời điểm (UTC)" value={detail.toTime}/>
        <Field label="Cách tiếp nhận" value={detail.provenance.acquisition === 'CSV_UPLOAD' ? 'CSV do Admin upload; nguồn gốc chưa xác minh' : detail.provenance.acquisition === 'DATABASE_EXPORT' ? 'Tiếp nhận trực tiếp từ database' : null}/>
        <Field label="Múi giờ nguồn" value={detail.provenance.sourceTimeZone}/>
      </dl>
      <RawDownload key={detail.snapshotId} detail={detail}/>
    </Stack></Paper>}
  </Stack>;
}
