import React from 'react';
import { Alert, Text } from '@mantine/core';
import { WdmApiError } from 'api/admin/data-mining';

export default function DatasetRequestError({ error, upload = false }: { error: WdmApiError | null; upload?: boolean }) {
  if (!error) return null;
  const messages: Record<number, string> = {
    400: upload ? 'Tệp hoặc thông tin upload chưa hợp lệ.' : 'Khoảng thời gian xuất dữ liệu chưa hợp lệ.',
    401: 'Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại.',
    403: 'Tài khoản không có quyền thực hiện thao tác này.',
    413: upload ? 'Tệp hoặc yêu cầu vượt giới hạn dung lượng của server. Hãy chọn tệp nhỏ hơn.' : 'Dữ liệu vượt giới hạn xuất. Hãy thu hẹp khoảng thời gian.',
    415: 'Định dạng tệp không được hỗ trợ. Vui lòng chọn tệp CSV.',
    503: 'Dịch vụ tạm thời chưa thể xử lý yêu cầu.',
  };
  const uncertain = upload && (error.kind !== 'http' || (error.status ?? 0) >= 500);
  return (
    <Alert role="alert" title={upload ? 'Chưa hoàn tất upload' : 'Chưa thể xuất CSV'} color="red">
      <Text size="sm">{messages[error.status ?? 0] || (error.kind === 'aborted' ? 'Thao tác đã bị hủy.' : 'Không thể xác nhận kết quả yêu cầu. Vui lòng kiểm tra kết nối.')}</Text>
      {uncertain && <Text size="sm">Yêu cầu có thể đã tạo snapshot. Hãy kiểm tra kết quả trước khi gửi lại để tránh tạo bản trùng.</Text>}
      {error.requestId && <Text size="sm">Mã yêu cầu: {error.requestId}</Text>}
    </Alert>
  );
}
