import React, { useState } from 'react';
import { Alert, Button, Group, Paper, Stack, Table, Text, Title } from '@mantine/core';
import { SnapshotDetail, ValidationDiagnostic, ValidationReport, WdmApiError } from 'api/admin/data-mining';
import { hasNextValidationPage } from 'api/admin/data-mining/validation-response';
import { useSnapshotValidation } from './useSnapshotValidation';

const explanations: Record<string, string> = {
  FIELD_REQUIRED: 'Thiếu giá trị bắt buộc.', FIELD_TOO_LONG: 'Giá trị vượt độ dài cho phép.',
  INVALID_INTEGER_FORMAT: 'Số nguyên không đúng định dạng.', VALUE_OUT_OF_RANGE: 'Giá trị ngoài phạm vi hỗ trợ.',
  STATUS_NOT_COMPLETED: 'Đơn chưa có trạng thái COMPLETED.', INVALID_TIMESTAMP: 'Thời điểm không đúng định dạng UTC hoặc không hợp lệ.',
  INVALID_MONEY_FORMAT: 'Giá trị tiền không đúng định dạng.', DISCOUNT_EXCEEDS_GROSS: 'Giảm giá vượt tổng giá dòng.',
  LINE_TOTAL_MISMATCH: 'Thành tiền không khớp dữ liệu dòng.', INVALID_CURRENCY_CODE: 'Mã tiền tệ không hợp lệ.',
  PRODUCT_NOT_FOUND: 'Không tìm thấy product ID khớp chính xác.', ORDER_FIELD_CONFLICT: 'Các dòng trong cùng đơn mâu thuẫn.',
  DUPLICATE_LINE_ID: 'Line ID lặp; xem kết quả cleaning để biết cách xử lý.',
  LINE_ID_CONTENT_CONFLICT: 'Cùng line ID nhưng nội dung mâu thuẫn.', LINE_ID_ORDER_CONFLICT: 'Một line ID thuộc nhiều đơn.',
  CSV_INVALID_UTF8: 'Không đọc được dữ liệu UTF-8.', CSV_INVALID_BOM: 'BOM đầu tệp không hợp lệ.',
  CSV_MISSING_HEADER: 'Thiếu header CSV.', CSV_HEADER_MISMATCH: 'Tên hoặc thứ tự cột không đúng contract.',
  CSV_UNCLOSED_QUOTE: 'Quoted field chưa đóng.', CSV_INVALID_QUOTE: 'Dấu quote sai vị trí.',
  CSV_INVALID_RECORD_SEPARATOR: 'Dấu kết thúc bản ghi không hợp lệ.', CSV_COLUMN_COUNT_MISMATCH: 'Bản ghi không đủ đúng 13 cột.',
  EMPTY_INPUT: 'Tệp chỉ có header, không có bản ghi dữ liệu.', EMPTY_AFTER_CLEANING: 'Không còn đơn sử dụng được sau cleaning.',
  BLOCKED_UNATTRIBUTABLE_ORDER: 'Có dòng không xác định được order ID hợp lệ; snapshot bị chặn toàn bộ.',
  NOT_RUN_STRUCTURAL_ERROR: 'Cleaning chưa chạy vì lỗi cấu trúc CSV.',
};
const stageLabels = { STRUCTURE: 'Cấu trúc CSV', BUSINESS: 'Nghiệp vụ', CONSISTENCY: 'Nhất quán đơn', CLEANING: 'Cleaning' };
const counters: Array<[keyof ValidationReport['counts'], string]> = [
  ['inputRecords', 'Dòng đầu vào'], ['keptRecords', 'Dòng giữ lại'], ['rejectedRecords', 'Dòng loại theo đơn'],
  ['deduplicatedRecords', 'Dòng lặp đã khử'], ['blockedRecords', 'Dòng bị chặn'], ['inputOrders', 'Tổng số đơn'],
  ['knownOrders', 'Đơn xác định được'], ['keptOrders', 'Đơn giữ lại'], ['rejectedOrders', 'Đơn bị loại'],
  ['businessInvalidRecords', 'Dòng sai nghiệp vụ'], ['issueCount', 'Số thông báo kiểm tra'],
];

function Failure({ error }: { error: WdmApiError }) {
  const messages: Record<number, string> = {
    400: 'Yêu cầu không hợp lệ. Hãy tải lại chi tiết snapshot.', 401: 'Phiên đăng nhập không còn hợp lệ.',
    403: 'Tài khoản không có quyền kiểm tra dữ liệu.', 404: 'Không tìm thấy snapshot này.',
    409: 'Chưa đọc được báo cáo hoàn tất hoặc trạng thái không cho phép thao tác. Có thể đang xử lý; hãy kiểm tra lại trạng thái.',
    503: 'Chưa xác nhận được kết quả. Máy chủ có thể đã lưu báo cáo; hãy kiểm tra lại trạng thái trước khi gửi yêu cầu mới.',
  };
  return <Alert role="alert" color="red" title="Chưa thể xác nhận báo cáo">
    <Text size="sm">{error.kind === 'response' ? 'Phản hồi báo cáo không đúng contract hoặc không khớp snapshot. Không thể hiển thị kết quả mới.'
      : messages[error.status ?? 0] || 'Kết nối bị gián đoạn; chưa xác nhận được kết quả. Hãy kiểm tra lại trạng thái.'}</Text>
    {error.requestId && <Text size="sm">Mã yêu cầu: {error.requestId}</Text>}
  </Alert>;
}

function Positions({ issue, index }: { issue: ValidationDiagnostic; index: number }) {
  const [page, setPage] = useState(0);
  const size = 5;
  return <Stack spacing="xs">
    {issue.positionAccuracy === 'DECODER_CURSOR' && <Text size="sm" color="orange">Vị trí con trỏ giải mã; không xác định chính xác byte hoặc bản ghi lỗi.</Text>}
    {!issue.positions.length ? <Text size="sm">Không có vị trí dòng cụ thể.</Text> : <>
      <Text size="sm">{issue.positions.length} vị trí; đang xem {page * size + 1}–{Math.min((page + 1) * size, issue.positions.length)}</Text>
      {issue.positions.slice(page * size, (page + 1) * size).map(position => <Text size="sm" key={position.recordNumber}>
        Bản ghi {position.recordNumber}, dòng {position.startLine}{position.endLine !== position.startLine ? '–' + position.endLine : ''}
      </Text>)}
      {issue.positions.length > size && <Group spacing="xs">
        <Button size="xs" variant="subtle" aria-label={'Vị trí trước cho thông báo ' + (index + 1)} disabled={page === 0} onClick={() => setPage(value => value - 1)}>Vị trí trước</Button>
        <Button size="xs" variant="subtle" aria-label={'Vị trí tiếp cho thông báo ' + (index + 1)} disabled={(page + 1) * size >= issue.positions.length} onClick={() => setPage(value => value + 1)}>Vị trí tiếp</Button>
      </Group>}
    </>}
  </Stack>;
}

function Report({ report, busy, read }: { report: ValidationReport; busy: boolean; read: (page: number) => Promise<void> }) {
  return <Stack>
    <Alert color={report.validationStatus === 'VALID' ? 'teal' : 'yellow'} title={report.validationStatus === 'VALID'
      ? 'Có dữ liệu sạch sử dụng được' : 'Chưa có dữ liệu sạch sử dụng được'}>
      {report.validationStatus === 'VALID' ? 'VALID xác nhận tập dữ liệu sau cleaning; tệp gốc vẫn có thể chứa đơn hoặc dòng đã bị loại.'
        : explanations[report.cleaningOutcome] || 'Kết quả validation là INVALID.'}
    </Alert>
    {!report.countsComplete && <Alert role="status" color="yellow" title="Chưa đọc hết dataset">
      Đã đọc {report.counts.parsedRecords} bản ghi hoàn chỉnh trước lỗi. Đây là tiền tố đã đọc, không phải tổng dataset; các tổng chưa xác định không được tính là 0.
    </Alert>}
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: 12 }}>
      {counters.map(([key, label]) => <div key={key} role="group" aria-label={label}><Text size="sm" color="dimmed">{label}</Text><Text>{report.counts[key] ?? 'Chưa xác định'}</Text></div>)}
    </div>
    <Text size="sm">Thời điểm kiểm tra (UTC): {report.completedAt}</Text>
    <Text size="sm">Phiên bản báo cáo: {report.reportVersion}; policy: {report.policyVersion}</Text>
    <Text size="sm" style={{ overflowWrap: 'anywhere' }}>SHA-256 của dữ liệu đã kiểm tra: {report.rawSha256}</Text>
    <Text size="sm" color="dimmed">Đọc lại báo cáo không kiểm tra lại tệp vật lý hoặc catalog. Bản ghi 1 là header; vị trí dòng tính cả newline trong quoted field.</Text>
    <Title order={4}>Thông báo kiểm tra</Title>
    {!report.issues.content.length ? <Text>{report.issues.totalElements === '0' ? 'Không có thông báo kiểm tra.' : 'Trang này không có thông báo.'}</Text>
      : <div style={{ overflowX: 'auto' }}><Table aria-label="Danh sách thông báo kiểm tra">
        <thead><tr><th>Giai đoạn</th><th>Mã và diễn giải</th><th>Cột</th><th>Vị trí nguồn</th></tr></thead>
        <tbody>{report.issues.content.map((issue, index) => <tr key={report.issues.page + ':' + index + ':' + issue.code}>
          <td>{stageLabels[issue.stage]}</td><td><Text weight={500}>{issue.code}</Text><Text size="sm">{explanations[issue.code] || 'Mã kiểm tra chưa có diễn giải; giữ mã gốc để đối soát.'}</Text></td>
          <td>{issue.field ?? '—'}</td><td><Positions issue={issue} index={index}/></td>
        </tr>)}</tbody>
      </Table></div>}
    <Group>
      <Button variant="light" disabled={busy || report.issues.page === 0} onClick={() => void read(report.issues.page - 1)}>Trang thông báo trước</Button>
      <Text size="sm">{report.issues.totalPages === '0' ? '0 thông báo'
        : 'Trang ' + (report.issues.page + 1) + ' / ' + report.issues.totalPages + '; ' + report.issues.totalElements + ' thông báo'}</Text>
      <Button variant="light" disabled={busy || !hasNextValidationPage(report)} onClick={() => void read(report.issues.page + 1)}>Trang thông báo tiếp</Button>
    </Group>
  </Stack>;
}

export default function SnapshotValidationPanel({ detail, onComplete }: { detail: SnapshotDetail; onComplete: (signal: AbortSignal) => Promise<void> }) {
  const state = useSnapshotValidation(detail, onComplete);
  const eligible = detail.validationStatus === 'PENDING' && detail.rawAvailability === 'AVAILABLE'
    && detail.conversionStatus === 'NOT_STARTED' && detail.provenance.contractVersion === 'wdm-order-csv-v1';
  const busy = state.busy !== null;
  return <Paper component="section" aria-label="Kiểm tra dữ liệu" withBorder radius="md" p="lg"><Stack>
    <Title order={3}>Kiểm tra dữ liệu</Title>
    <Text size="sm">Kiểm tra CSV, nghiệp vụ và nhất quán đơn, sau đó áp dụng cleaning. Không sửa tệp gốc hoặc chạy mining.</Text>
    {!state.report && !busy && !state.error && <Text>Chưa có báo cáo được đọc trong phiên xem này.</Text>}
    {!eligible && !state.report && <Text size="sm">Chưa thể bắt đầu từ trạng thái hiện tại hoặc tệp gốc chưa sẵn sàng. Báo cáo đã lưu vẫn có thể được đọc lại.</Text>}
    {state.error && <Failure error={state.error}/>}
    {state.recheckRequired && <Text role="status">Hãy kiểm tra lại trạng thái trước khi gửi yêu cầu validation mới.</Text>}
    <Group>
      {!state.report && <Button loading={state.busy === 'post'} disabled={busy || !eligible || state.recheckRequired
        || state.error?.kind === 'response' || [400, 404].includes(state.error?.status ?? 0)} onClick={() => void state.start()}>Kiểm tra dữ liệu</Button>}
      <Button variant="light" loading={state.busy === 'read'} disabled={busy} onClick={() => void state.read()}>Kiểm tra lại trạng thái</Button>
    </Group>
    {busy && <Text role="status">{state.busy === 'post' ? 'Đang nhận kết quả kiểm tra và cập nhật metadata…' : 'Đang đọc báo cáo…'}</Text>}
    <Text size="sm" color="dimmed">Rời trang hoặc ngừng chờ không hủy xử lý trên máy chủ. UI không tự gửi lại POST sau lỗi kết nối.</Text>
    {state.report && <Report report={state.report} busy={busy} read={state.read}/>}
  </Stack></Paper>;
}
