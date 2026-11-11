import { WdmApiError } from 'api/admin/data-mining';

export const RUN_POLL_DELAY = 2000, RUN_POLL_BUDGET = 120000, RUN_GET_TIMEOUT = 15000, RUN_POST_TIMEOUT = 45000;
/** Bound even a transport ignoring AbortSignal; late resolution never updates the caller. */
export function boundedRunRequest<T>(work: () => Promise<T>, controller: AbortController, milliseconds: number): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const abort = () => reject(new WdmApiError('aborted', null, 'REQUEST_ABORTED', 'Request was cancelled.'));
    const timer = window.setTimeout(() => { reject(new WdmApiError('network', null, 'RUN_REQUEST_TIMEOUT', 'Run request timed out.')); controller.abort(); }, milliseconds);
    controller.signal.addEventListener('abort', abort);
    const cleanup = () => { window.clearTimeout(timer); controller.signal.removeEventListener('abort', abort); controller.signal.removeEventListener('abort', cleanup); };
    if (controller.signal.aborted) { cleanup(); abort(); return; }
    Promise.resolve().then(() => {
      if (controller.signal.aborted) throw new WdmApiError('aborted', null, 'REQUEST_ABORTED', 'Request was cancelled.');
      return work();
    }).then(resolve, reject).finally(cleanup);
    controller.signal.addEventListener('abort', cleanup, { once: true });
  });
}
export function runRequestError(failure: unknown): WdmApiError {
  return failure instanceof WdmApiError ? failure : new WdmApiError('response', null, 'INVALID_RUN_RESPONSE', 'Run response could not be confirmed.');
}
export function runErrorMessage(error: WdmApiError): string {
  return ({ 400: 'Dữ liệu không hợp lệ. Hãy kiểm tra lại ID và cấu hình.', 401: 'Phiên đăng nhập không còn hợp lệ.', 403: 'Tài khoản không có quyền Admin.',
    404: 'Không tìm thấy tài nguyên được yêu cầu.', 409: 'Dữ liệu hoặc trạng thái đã thay đổi. Hãy kiểm tra lại.',
    422: 'Cấu hình ngoài phạm vi hỗ trợ. Ngưỡng hiệu dụng zero chưa được hỗ trợ.', 503: 'Dịch vụ chưa thể tiếp nhận hoặc trả lời yêu cầu.' } as Record<number, string>)[error.status ?? 0]
    || 'Chưa thể xác nhận phản hồi. Hãy kiểm tra kết nối và đọc lại bằng thao tác của bạn.';
}
