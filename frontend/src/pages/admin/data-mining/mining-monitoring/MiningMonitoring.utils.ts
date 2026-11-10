import type { MiningRunSummary, MiningStatus } from '../../../../api/admin/data-mining/history/MiningRun.types';

export const statusPresentation: Record<MiningStatus, { label: string; color: string }> = {
  QUEUED: { label: 'Đang chờ thực thi', color: 'gray' },
  RUNNING: { label: 'Đang thực thi thuật toán', color: 'blue' },
  SUCCEEDED: { label: 'Hoàn thành thành công', color: 'green' },
  FAILED: { label: 'Thực thi thất bại', color: 'red' },
  LEGACY_UNVERIFIED: { label: 'Dữ liệu cũ chưa xác minh', color: 'gray' },
};

function timestamp(value: string | null): number | null {
  if (!value) return null;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : null;
}

/** Wall-clock elapsed time, distinct from the measured totalExecutionMs scope. */
export function elapsedMs(run: MiningRunSummary, observedAt: number): number | null {
  if (run.status !== 'RUNNING' && run.status !== 'SUCCEEDED' && run.status !== 'FAILED') return null;
  const start = timestamp(run.startedAt);
  const end = run.status === 'RUNNING' ? observedAt : timestamp(run.finishedAt);
  if (start === null || end === null || !Number.isFinite(end) || end < start) return null;
  const elapsed = end - start;
  return Number.isFinite(elapsed) ? elapsed : null;
}

export function metric(value: number | null | undefined, unit = ''): string {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0
    ? `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 }).format(value)}${unit ? ` ${unit}` : ''}`
    : 'Chưa có dữ liệu';
}

export function timestampText(value: string | null): string {
  return timestamp(value) === null ? 'Chưa có dữ liệu' : value as string;
}

export function safeExecutionError(message: string | null): string {
  // Mirrors the mapper's public diagnostic policy; never render arbitrary server text.
  return message?.trim()
    ? 'RUN có thông tin lỗi được ghi nhận; chi tiết chẩn đoán không được công bố qua API.'
    : 'RUN thất bại; chưa có thông báo lỗi công khai.';
}
