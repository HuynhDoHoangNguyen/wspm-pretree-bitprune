import { formatStoredMetric, hasCurrentMetricScope, metricAvailability, miningMetricRows } from './mining-run-metrics';
import { legacyMeasuredRun, measuredRun, runStatus } from 'api/admin/data-mining/mining-run-test-data';

it.each([['0', '0'], ['1', '1'], ['999', '999'], ['1000', '1.000'], ['9007199254740993', '9.007.199.254.740.993'],
  ['18446744073709551615', '18.446.744.073.709.551.615']])('formats exact stored digits %s without numeric coercion', (raw, formatted) => {
  expect(formatStoredMetric(raw)).toBe(formatted); expect(formatted.replace(/\./g, '')).toBe(raw);
});
it('keeps absent measurements distinct from zero', () => {
  expect(formatStoredMetric(null)).toBe('Chưa có số đo'); expect(formatStoredMetric('0')).toBe('0');
});
it('projects all nine observations without changing native values, scopes or the report', () => {
  const report = measuredRun(), before = JSON.stringify(report);
  Object.freeze(report.metrics); Object.freeze(report.measurement); Object.freeze(report);
  const rows = miningMetricRows(report), all = [...rows.counts, ...rows.timings, ...rows.heap];
  expect(all.map(row => row.key)).toEqual(['patternCount', 'candidateCount', 'datasetIoMs', 'preprocessingMs', 'dfsMiningMs',
    'completeAlgorithmMs', 'outputIoMs', 'totalExecutionMs', 'peakSampledJvmHeapBytes']);
  expect(all.map(row => row.value)).toEqual(['0', '9007199254740993', '0', '2', '3', '6', null, '10', '18446744073709551615']);
  expect(rows.timings.map(row => row.unit)).toEqual(['ms', 'ms', 'ms', 'ms', 'ms', 'ms']);
  expect(rows.heap[0].unit).toBe('bytes'); expect(JSON.stringify(report)).toBe(before);
});
it.each(['WSPM_PRETREE', 'WSPM_BITPRUNE'] as const)('uses native candidate semantics for verified %s only', algorithm => {
  const report = measuredRun(algorithm), rows = miningMetricRows(report);
  expect(rows.counts[1].label).toBe(algorithm === 'WSPM_PRETREE' ? 'Ứng viên WSPM_PreTree' : 'Extensions đã sinh WSPM_BitPrune');
  expect(rows.counts[1].scope).toContain('loại trừ F1'); expect(rows.counts[1].value).toBe(report.candidateCount);
});
it('does not infer current scope from algorithm, status or populated historical values', () => {
  const legacy = legacyMeasuredRun(); expect(hasCurrentMetricScope(legacy)).toBe(false);
  const rows = miningMetricRows(legacy);
  expect(rows.counts[1].label).toBe('Bộ đếm ứng viên đã lưu'); expect(rows.heap[0].label).toBe('Heap lấy mẫu đã lưu');
  expect(rows.timings[5].label).toBe('Tổng thời gian đã lưu');
  expect([...rows.counts, ...rows.timings, ...rows.heap].every(row => row.scope.includes('chưa được xác minh'))).toBe(true);
  const wrongDescriptor = measuredRun(); wrongDescriptor.measurement.heapScope = null; expect(hasCurrentMetricScope(wrongDescriptor)).toBe(false);
});
it('distinguishes known absent output, historical missing output and measured output zero', () => {
  expect(miningMetricRows(measuredRun()).timings[4].missingText).toBe('Chưa có bước ghi kết quả');
  expect(miningMetricRows(legacyMeasuredRun()).timings[4].missingText).toBeUndefined();
  const zero = measuredRun(); zero.metrics.outputIoMs = '0';
  expect(miningMetricRows(zero).timings[4].missingText).toBeUndefined(); expect(miningMetricRows(zero).timings[4].value).toBe('0');
});
it.each([
  ['QUEUED', 'Chưa công bố số đo hoàn tất.'], ['RUNNING', 'Chưa công bố số đo hoàn tất.'], ['FAILED', 'Không có bộ số đo hoàn tất.'],
  ['SUCCEEDED', 'Run đã hoàn tất; bản ghi chưa có số đo.'], ['LEGACY_UNVERIFIED', 'Dữ liệu lịch sử chưa có số đo đã lưu.'],
] as const)('describes missing measurements for %s without inventing zero or changing status', (status, message) => {
  const report = runStatus(status); report.patternCount = null;
  expect(metricAvailability(report)).toBe(message); expect(report.status).toBe(status);
});
