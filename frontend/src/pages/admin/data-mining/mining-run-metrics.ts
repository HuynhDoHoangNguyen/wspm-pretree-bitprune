import { MiningRunStatus } from 'api/admin/data-mining';

export interface MiningMetricRow {
  key: string; label: string; value: string | null; unit: 'ms' | 'bytes' | null; scope: string; missingText?: string;
}
const unknownScope = 'Giá trị đã lưu; phạm vi đo chưa được xác minh.';
/** Group canonical decimal digits without passing through a numeric representation. */
export function formatStoredMetric(value: string | null): string {
  return value === null ? 'Chưa có số đo' : value.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}
export function hasCurrentMetricScope(report: MiningRunStatus): boolean {
  const m = report.measurement;
  return m.contractVersion === 'WDM-05.6' && m.timeUnit === 'MILLISECONDS' && m.memoryUnit === 'BYTES'
    && m.datasetIoScope === 'WORKER_VERIFIED_RAW_SEQUENCE_MAPPING_READS'
    && m.totalExecutionScope === 'POST_CLAIM_REFRESH_THROUGH_ENGINE_RETURN' && m.heapScope === 'ALGORITHM_JVM_WIDE_USED_HEAP_SAMPLING';
}
export function miningMetricRows(report: MiningRunStatus): { counts: MiningMetricRow[]; timings: MiningMetricRow[]; heap: MiningMetricRow[] } {
  const known = hasCurrentMetricScope(report), m = report.metrics;
  const timing = (key: keyof MiningRunStatus['metrics'], label: string, scope: string): MiningMetricRow => ({ key, label, value: m[key], unit: 'ms', scope: known ? scope : unknownScope });
  return {
    counts: [
      { key: 'patternCount', label: 'Số patterns', value: report.patternCount, unit: null, scope: known ? 'Pattern count do backend công bố; có thể bằng 0.' : unknownScope },
      { key: 'candidateCount', label: known ? report.algorithm === 'WSPM_PRETREE' ? 'Ứng viên WSPM_PreTree' : 'Extensions đã sinh WSPM_BitPrune' : 'Bộ đếm ứng viên đã lưu',
        value: report.candidateCount, unit: null, scope: known ? 'Bộ đếm native loại trừ F1. Hai thuật toán có định nghĩa bộ đếm riêng.' : unknownScope },
    ],
    timings: [
      timing('datasetIoMs', 'Đọc và xác minh dữ liệu', 'Ba verified read raw/sequence/mapping, gồm kiểm tra integrity; loại trừ DB lookup và parsing.'),
      timing('preprocessingMs', 'Preprocessing', 'Thời gian preprocessing do core đo.'),
      timing('dfsMiningMs', 'DFS mining', 'Thời gian DFS mining do core đo.'),
      timing('completeAlgorithmMs', 'Toàn thuật toán', 'Scope core bao gồm preprocessing, DFS và finalization.'),
      { ...timing('outputIoMs', 'Ghi output', 'Scope ghi kết quả, khi có phép đo.'),
        ...(known && m.outputIoMs === null ? { missingText: 'Chưa có bước ghi kết quả', scope: 'Contract WDM-05.6 hiện chưa có output stage.' } : {}) },
      timing('totalExecutionMs', known ? 'Tổng scope worker' : 'Tổng thời gian đã lưu', 'Từ sau claim, trước refresh đến engine return; loại trừ pre-admission, queue, claim và terminal DB finalization.'),
    ],
    heap: [{ key: 'peakSampledJvmHeapBytes', label: known ? 'JVM heap lấy mẫu trong thuật toán' : 'Heap lấy mẫu đã lưu', value: m.peakSampledJvmHeapBytes, unit: 'bytes',
      scope: known ? 'JVM-wide used heap sampling trong thuật toán; chịu ảnh hưởng của hoạt động khác trong JVM. Không phải RSS, RAM vật lý, cấp phát riêng của run hoặc peak toàn worker.' : unknownScope }],
  };
}
export function metricAvailability(report: MiningRunStatus): string {
  const hasValue = [report.patternCount, report.candidateCount, ...Object.values(report.metrics)].some(value => value !== null);
  if (hasValue) return 'Các giá trị dưới đây lấy từ cùng snapshot trạng thái đã xác nhận.';
  if (report.status === 'QUEUED' || report.status === 'RUNNING') return 'Chưa công bố số đo hoàn tất.';
  if (report.status === 'FAILED') return 'Không có bộ số đo hoàn tất.';
  if (report.status === 'SUCCEEDED') return 'Run đã hoàn tất; bản ghi chưa có số đo.';
  return 'Dữ liệu lịch sử chưa có số đo đã lưu.';
}
