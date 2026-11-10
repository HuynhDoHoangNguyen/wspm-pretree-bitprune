import { MiningRunDetail } from '../mining-run/MiningRun.types';
import { ComparisonResult, PreviewPairCheck } from './MiningComparison.types';

export const nonNegative = (value: number | null | undefined): value is number =>
  typeof value === 'number' && Number.isFinite(value) && value >= 0;
const positive = (value: number | null | undefined): value is number => nonNegative(value) && value > 0;
const finiteResult = (value: number) => Number.isFinite(value) ? value : null;

export function difference(a: number | null, b: number | null): number | null {
  return nonNegative(a) && nonNegative(b) ? finiteResult(b - a) : null;
}

export function compareRuns(a: MiningRunDetail, b: MiningRunDetail, check?: PreviewPairCheck): ComparisonResult {
  const reasons: string[] = [];
  if (![a.minsupMode, b.minsupMode].every(mode => ['ABSOLUTE', 'RELATIVE'].includes(mode))) {
    reasons.push('Mode support đã lưu chưa được xác lập cho phép so sánh này; không tự chuyển PERCENT thành RELATIVE hoặc tính lại ngưỡng.');
  }
  if (a.algorithm !== 'WSPM_PRETREE' || b.algorithm !== 'WSPM_BITPRUNE') reasons.push('Phải chọn PreTree cho RUN A và BitPrune cho RUN B.');
  if (a.status !== 'SUCCEEDED' || b.status !== 'SUCCEEDED') reasons.push('Cả hai RUN phải hoàn thành thành công.');
  if (!a.snapshotId || !b.snapshotId || a.snapshotId !== b.snapshotId || !a.datasetSource || a.datasetSource !== b.datasetSource) reasons.push('Dataset/snapshot không trùng hoặc chưa được xác định.');
  if (!positive(a.sequenceCount) || a.sequenceCount !== b.sequenceCount || !positive(a.distinctItemCount) || a.distinctItemCount !== b.distinctItemCount || a.validationStatus !== 'VALID' || b.validationStatus !== 'VALID') reasons.push('Thông tin kiểm tra dataset chưa tương đương hoặc còn thiếu.');
  if (!['ABSOLUTE', 'RELATIVE'].includes(a.minsupMode) || a.minsupMode !== b.minsupMode ||
      !positive(a.minsupConfigured) || a.minsupConfigured !== b.minsupConfigured ||
      !positive(a.effectiveThreshold) || a.effectiveThreshold !== b.effectiveThreshold ||
      a.minsupRelativeFraction !== b.minsupRelativeFraction ||
      (a.minsupRelativeFraction !== null && (!positive(a.minsupRelativeFraction) || a.minsupRelativeFraction > 1)) ||
      (a.minsupMode === 'RELATIVE' && (!positive(a.minsupRelativeFraction) || a.minsupRelativeFraction > 1))) {
    reasons.push('Minsup cấu hình/ngưỡng hiệu lực khác nhau hoặc chưa đủ thông tin (không hỗ trợ ngưỡng 0).');
  }
  if ((['maxW', 'minW', 'meanW'] as const).some(field => !nonNegative(a[field]) || a[field] !== b[field])) reasons.push('Tham số MaxW/MinW/MeanW khác nhau hoặc còn thiếu.');
  const matchedCheck = check?.origin === 'PREVIEW_ONLY' && check.preTreeRunId === a.runId && check.bitPruneRunId === b.runId;
  if (!matchedCheck || !check?.configurationVerified) reasons.push('Chưa có bằng chứng về toàn bộ cấu hình mining; DTO chưa có K và các cấu hình bổ sung.');
  if (!matchedCheck || !check?.measurementConditionsVerified) reasons.push('Chưa xác nhận cùng điều kiện đo JVM/phần cứng và cùng scope thời gian.');
  const countsValid = nonNegative(a.patternCount) && Number.isSafeInteger(a.patternCount) && nonNegative(b.patternCount) && Number.isSafeInteger(b.patternCount);
  if (!countsValid) reasons.push('Thiếu số pattern hợp lệ để đối chiếu.');
  else if (a.patternCount !== b.patternCount) reasons.push('Số pattern khác nhau — dừng đối chiếu hiệu năng và cần kiểm tra tính đúng đắn.');
  if (!matchedCheck || check?.fullPatternSupportMatch !== true) reasons.push('Chưa xác nhận toàn bộ pattern → exact support trùng nhau, hoặc kết quả đang khác nhau.');
  const eligible = reasons.length === 0;
  const aRuntime = a.totalExecutionMs, bRuntime = b.totalExecutionMs;
  const aHeap = a.peakSampledJvmHeapBytes, bHeap = b.peakSampledJvmHeapBytes;
  const runtimeValid = eligible && positive(aRuntime) && positive(bRuntime);
  const memoryValid = eligible && positive(aHeap) && nonNegative(bHeap);
  return {
    eligible, reasons,
    speedup: runtimeValid ? finiteResult(aRuntime / bRuntime) : null,
    runtimeReduction: runtimeValid ? finiteResult((aRuntime - bRuntime) / aRuntime * 100) : null,
    memoryReduction: memoryValid ? finiteResult((aHeap - bHeap) / aHeap * 100) : null,
  };
}

export function displayNumber(value: number | null | undefined, unit = '') {
  return typeof value === 'number' && Number.isFinite(value)
    ? `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 4 }).format(value)}${unit ? ` ${unit}` : ''}`
    : 'Chưa có dữ liệu';
}
