import React, { useEffect, useRef, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { Alert, Anchor, Badge, Button, Group, Paper, Stack, Text, Title } from '@mantine/core';
import { datasetApi, SnapshotDetail, ValidationReport, WdmApiError } from 'api/admin/data-mining';
import useTitle from 'hooks/use-title';
import { useDatasetRequest } from './useDatasetRequest';
import { downloadDatasetFile } from './downloadDatasetFile';
import SnapshotValidationPanel from './SnapshotValidationPanel';
import SnapshotConversionPanel from './SnapshotConversionPanel';
import useWorkspaceStyles from './workspace.styles';
import CopyValue from './CopyValue';

const tabs = [{ key: 'source', label: '1. Dữ liệu gốc' }, { key: 'validation', label: '2. Kiểm tra dữ liệu' },
  { key: 'conversion', label: '3. Chuyển đổi & thống kê' }];
function initialTab(detail: SnapshotDetail | null) {
  if (detail?.conversionStatus && detail.conversionStatus !== 'NOT_STARTED') return 'conversion';
  if (detail?.validationStatus === 'VALID') return 'conversion';
  if (['PENDING', 'INVALID'].includes(detail?.validationStatus || '')) return 'validation';
  return 'source';
}

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
  return <div><dt><Text color="dimmed" data-secondary size="sm">{label}</Text></dt>
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
  const { classes } = useWorkspaceStyles();
  const [params, setParams] = useSearchParams();
  const [selection, setSelection] = useState({ id: '', tab: 'source' });
  const tabButtons = useRef<Array<HTMLButtonElement | null>>([]);
  const [revision, setRevision] = useState(0);
  const [validationReport, setValidationReport] = useState<ValidationReport | null>(null);
  const [validationActivity, setValidationActivity] = useState({ busy: false, error: false });
  const [conversionActivity, setConversionActivity] = useState({ busy: false, error: false });
  const [metadataRefreshFailed, setMetadataRefreshFailed] = useState(false);
  const [state, setState] = useState<{ id: string; loading: boolean; detail: SnapshotDetail | null; error: WdmApiError | null }>({ id: '', loading: true, detail: null, error: null });
  useEffect(() => {
    const controller = new AbortController();
    setMetadataRefreshFailed(false);
    setValidationReport(null);
    setValidationActivity({ busy: false, error: false }); setConversionActivity({ busy: false, error: false });
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
  const requested = params.get('tab');
  const selected = tabs.some(tab => tab.key === requested) ? requested : selection.id === id ? selection.tab : initialTab(detail);
  useEffect(() => {
    if (detail && selection.id !== id) setSelection({ id, tab: initialTab(detail) });
  }, [detail, id, selection.id]);
  function selectTab(tab: string) {
    setSelection({ id, tab });
    const next = new URLSearchParams(params); next.set('tab', tab); setParams(next, { replace: true });
  }
  async function refreshValidationMetadata(signal: AbortSignal) {
    setMetadataRefreshFailed(false);
    try {
      const next = await datasetApi.detail(id, signal);
      if (!signal.aborted) setState(current => current.id === id ? { ...current, detail: next } : current);
    } catch {
      if (!signal.aborted) setMetadataRefreshFailed(true);
    }
  }
  const validations = { PENDING: 'PENDING — Chưa có kết quả hoàn tất', VALID: 'VALID — Có dữ liệu sạch sử dụng được', INVALID: 'INVALID — Chưa có dữ liệu sạch sử dụng được', LEGACY_UNVERIFIED: 'Dữ liệu cũ chưa xác minh' };
  const nextTab = initialTab(detail);
  const nextAction = nextTab === 'source' ? 'Xem dữ liệu gốc' : nextTab === 'validation' ? 'Mở bước kiểm tra'
    : detail?.conversionStatus === 'SUCCEEDED' ? 'Xem kết quả chuyển đổi' : detail?.conversionStatus === 'RUNNING' ? 'Theo dõi chuyển đổi'
      : detail?.conversionStatus === 'FAILED' ? 'Xem lỗi chuyển đổi' : 'Mở bước chuyển đổi';
  return <div className={classes.root}><Stack spacing="lg">
    <Anchor component={Link} to="/admin/datasets">Về trang dữ liệu</Anchor>
    <div className={classes.hero}><Stack spacing="sm">
      <Group position="apart"><Title order={2}>Chi tiết snapshot</Title><Group spacing="xs">
        {detail && <Badge color={detail.conversionStatus === 'SUCCEEDED' ? 'teal' : detail.validationStatus === 'INVALID' || detail.conversionStatus === 'FAILED' ? 'red' : 'blue'}>
          {detail.conversionStatus === 'SUCCEEDED' ? 'Đã chuyển đổi' : detail.validationStatus === 'INVALID' ? 'Cần xử lý dữ liệu'
            : detail.conversionStatus === 'FAILED' ? 'Chuyển đổi thất bại' : detail.conversionStatus === 'RUNNING' ? 'Đang chuyển đổi'
              : detail.validationStatus === 'VALID' ? 'Đã kiểm tra dữ liệu' : detail.validationStatus === 'PENDING' ? 'Chờ kiểm tra dữ liệu'
                : detail.validationStatus === 'LEGACY_UNVERIFIED' ? 'Dữ liệu cũ chưa xác minh' : 'Chưa xác nhận trạng thái'}</Badge>}
        <Button variant="subtle" onClick={() => setRevision(value => value + 1)} disabled={loading}>Tải lại chi tiết</Button>
      </Group></Group>
      <Text>Kiểm tra dữ liệu trước khi chuyển đổi. Mỗi bước giữ lại kết quả để bạn có thể xem và đối soát.</Text>
      {detail && <Group position="apart"><CopyValue label="Snapshot ID" value={detail.snapshotId}/><Button onClick={() => selectTab(nextTab)}>{nextAction}</Button></Group>}
    </Stack></div>
    {loading && <Text role="status">Đang tải chi tiết snapshot…</Text>}
    {!loading && state.error && <Failure error={state.error}/>}
    {metadataRefreshFailed && <Alert role="alert" color="yellow">Đã nhận báo cáo nhưng chưa tải lại được metadata. Báo cáo vẫn được giữ; hãy tải lại chi tiết.</Alert>}
    {(validationActivity.error || conversionActivity.error) && <Alert color="yellow" role="status">
      Có thông báo cần xem tại bước {validationActivity.error ? 'Kiểm tra dữ liệu' : 'Chuyển đổi & thống kê'}. Hãy mở bước đó để xem chi tiết và kiểm tra lại trạng thái.
    </Alert>}
    {detail && <>
      <Paper withBorder radius="md" p="md"><dl className={classes.summary}>
        <Field label="Tên nguồn" value={detail.sourceName}/>
        <Field label="Trạng thái kiểm tra" value={detail.validationStatus ? validations[detail.validationStatus] : null}/>
        <Field label="Chuyển đổi sequence" value={detail.conversionStatus === 'NOT_STARTED' ? 'Chưa chuyển đổi' : detail.conversionStatus}/>
        <Field label="Thống kê" value={detail.statisticsStatus === 'NOT_COMPUTED' ? 'Chưa tính' : detail.statisticsStatus === 'COMPUTED' ? 'Đã tính' : null}/>
        <Field label="Số sequence" value={detail.sequenceCount}/>
      </dl></Paper>
      <div role="tablist" aria-label="Các bước xử lý snapshot" className={classes.tabs}>
        {tabs.map((tab, index) => <button type="button" key={tab.key} ref={element => { tabButtons.current[index] = element; }}
          id={'snapshot-tab-' + tab.key} role="tab" aria-selected={selected === tab.key} aria-controls={'snapshot-panel-' + tab.key}
          tabIndex={selected === tab.key ? 0 : -1} className={classes.tab} onClick={() => selectTab(tab.key)}
          onKeyDown={event => {
            let next: number;
            if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
            else if (event.key === 'ArrowLeft') next = (index + tabs.length - 1) % tabs.length;
            else if (event.key === 'Home') next = 0;
            else if (event.key === 'End') next = tabs.length - 1;
            else return;
            event.preventDefault(); selectTab(tabs[next].key); tabButtons.current[next]?.focus();
          }}>{tab.label}{(tab.key === 'validation' ? validationActivity.busy : tab.key === 'conversion' ? conversionActivity.busy : false) && <Badge color="blue" size="sm">Đang xử lý</Badge>}
          {(tab.key === 'validation' ? validationActivity.error : tab.key === 'conversion' ? conversionActivity.error : false) && <Badge color="orange" size="sm">Cần xem thông báo</Badge>}</button>)}
      </div>
      <Text size="sm" color="dimmed" data-secondary>Chọn một bước để xem dữ liệu. Kết quả được giữ lại khi bạn chuyển bước.</Text>
      {/* Keep all panels mounted: hiding a tab must never restart its request lifecycle. */}
      <div role="tabpanel" id="snapshot-panel-source" aria-labelledby="snapshot-tab-source" hidden={selected !== 'source'}>
        <Paper withBorder radius="md" p="lg"><Stack spacing="lg">
          <Title order={3}>Dữ liệu gốc</Title>
          <dl className={classes.fields}>
            <Field label="Loại nguồn" value={detail.sourceType}/>
            <Field label="Dung lượng gốc" value={detail.rawSizeBytes === null ? null : detail.rawSizeBytes + ' byte'}/>
            <Field label="Thời điểm tạo (UTC)" value={detail.createdAt}/>
            <Field label="Từ thời điểm (UTC)" value={detail.fromTime}/>
            <Field label="Đến thời điểm (UTC)" value={detail.toTime}/>
            <Field label="Cách tiếp nhận" value={detail.provenance.acquisition === 'CSV_UPLOAD' ? 'CSV do Admin upload; nguồn gốc chưa xác minh' : detail.provenance.acquisition === 'DATABASE_EXPORT' ? 'Tiếp nhận trực tiếp từ database' : null}/>
            <Field label="Múi giờ nguồn" value={detail.provenance.sourceTimeZone}/>
          </dl>
          <details><summary>Thông tin kỹ thuật & checksum</summary><dl className={classes.fields}>
            <Field label="Snapshot ID" value={detail.snapshotId}/><Field label="SHA-256 khi tiếp nhận" value={detail.rawSha256}/>
          </dl></details>
          <RawDownload key={detail.snapshotId} detail={detail}/>
        </Stack></Paper></div>
      <div role="tabpanel" id="snapshot-panel-validation" aria-labelledby="snapshot-tab-validation" hidden={selected !== 'validation'}>
        <SnapshotValidationPanel key={'validation:' + detail.snapshotId} detail={detail} onComplete={refreshValidationMetadata} onReportChange={setValidationReport} onActivityChange={setValidationActivity}/>
        {detail.validationStatus === 'VALID' && validationReport?.validationStatus === 'VALID' && <Button mt="md" onClick={() => selectTab('conversion')}>Tiếp tục chuyển đổi</Button>}
      </div>
      <div role="tabpanel" id="snapshot-panel-conversion" aria-labelledby="snapshot-tab-conversion" hidden={selected !== 'conversion'}>
        <SnapshotConversionPanel key={'conversion:' + detail.snapshotId} detail={detail} validationReport={validationReport} onComplete={refreshValidationMetadata} onActivityChange={setConversionActivity}/>
      </div>
    </>}
  </Stack></div>;
}
