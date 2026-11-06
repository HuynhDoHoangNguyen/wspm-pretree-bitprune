import React from 'react';
import { Alert, Paper, Stack, Text, Title } from '@mantine/core';
import { MiningPreview, MiningValidation, WdmApiError } from 'api/admin/data-mining';

const labels = { sequenceCount: 'Số sequence', singleItemsetSequenceCount: 'Sequence một itemset', multiItemsetSequenceCount: 'Sequence nhiều itemset',
  itemsetCount: 'Số itemset', distinctItemCount: 'Số item khác nhau', totalItemOccurrences: 'Tổng lượt xuất hiện item',
  minSequenceLength: 'Độ dài sequence nhỏ nhất', maxSequenceLength: 'Độ dài sequence lớn nhất', averageSequenceLength: 'Độ dài sequence trung bình' };
export default function MiningConfigResult({ preview, validation, error }: { preview: MiningPreview | null; validation: MiningValidation | null; error: WdmApiError | null }) {
  const support = validation?.minimumSupport ?? preview?.minimumSupport;
  const zero = error?.status === 422 && error.code === 'UNSUPPORTED_MINING_CONFIGURATION'
    && error.details.some(d => d.field === 'minsupValue' && d.code === 'ZERO_EFFECTIVE_THRESHOLD');
  const messages: Record<number, string> = { 400: 'Dữ liệu cấu hình không hợp lệ. Hãy kiểm tra các trường.', 401: 'Phiên đăng nhập không còn hợp lệ.',
    403: 'Tài khoản không có quyền Admin.', 404: 'Không tìm thấy snapshot.', 409: 'Snapshot chưa sẵn sàng hoặc dữ liệu không nhất quán.',
    503: 'Chưa thể đọc hoặc xác minh dữ liệu. Bạn có thể thử lại sau.' };
  return <Stack aria-live="polite">
    {error && <Alert role="alert" color="red" title={zero ? 'Cấu hình ngoài phạm vi hỗ trợ' : 'Chưa thể xác nhận cấu hình'}>
      <Text>{zero ? 'Ngưỡng hiệu dụng bằng zero. Hãy điều chỉnh minsup; ngữ nghĩa ngưỡng zero chưa được nghiệm thu.'
        : error.code === 'MINING_BINDING_CHANGED' ? 'Thông tin snapshot đã thay đổi. Hãy xem trước và kiểm tra lại.'
          : messages[error.status ?? 0] || 'Không thể xác nhận phản hồi. Hãy kiểm tra kết nối và thử lại bằng thao tác của bạn.'}</Text>
      {error.requestId && <Text size="sm">Mã yêu cầu: {error.requestId}</Text>}
    </Alert>}
    {validation && <Alert role="status" color="teal" title="Cấu hình thuộc phạm vi hỗ trợ">
      Cấu hình đã qua kiểm tra tại thời điểm này. Chưa tạo lần chạy; khi thực thi, hệ thống phải xác minh lại dữ liệu.
    </Alert>}
    {preview && !validation && <Text role="status">Đã xem trước dữ liệu; chưa xác nhận cấu hình thuộc phạm vi hỗ trợ.</Text>}
    {preview && <Paper withBorder p="md"><Title order={3}>Thống kê dataset</Title><dl>
      {(Object.keys(labels) as Array<keyof typeof labels>).map(key => <div key={key}><dt>{labels[key]}</dt><dd>{String(preview.statistics[key])}</dd></div>)}
    </dl></Paper>}
    {support && <Paper withBorder p="md"><Title order={3}>Ngưỡng từ backend</Title><dl>
      <dt>Mode</dt><dd>{support.mode}</dd><dt>Giá trị đã cấu hình</dt><dd>{String(support.configuredValue)}</dd>
      <dt>Tỷ lệ đã resolve</dt><dd>{support.relativeFraction === null ? 'Không áp dụng' : String(support.relativeFraction)}</dd>
      <dt>Ngưỡng hiệu dụng</dt><dd>{String(support.effectiveThreshold)}</dd>
    </dl></Paper>}
  </Stack>;
}
