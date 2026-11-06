import { WdmApiError } from './client';

export interface ValidationPosition { recordNumber: number; startLine: number; endLine: number }
export interface ValidationDiagnostic {
  stage: 'STRUCTURE' | 'BUSINESS' | 'CONSISTENCY' | 'CLEANING';
  code: string;
  field: string | null;
  positionAccuracy: 'DECODER_CURSOR' | 'RECORD_RANGE';
  positions: ValidationPosition[];
}
const countKeys = ['parsedRecords', 'inputRecords', 'inputOrders', 'knownOrders', 'businessInvalidRecords',
  'keptRecords', 'rejectedRecords', 'deduplicatedRecords', 'blockedRecords', 'keptOrders', 'rejectedOrders', 'issueCount'] as const;
export type ValidationCounts = Record<typeof countKeys[number], string | null>;
export interface ValidationReport {
  snapshotId: string; reportVersion: 'WDM_VALIDATION_REPORT_V1'; contractVersion: 'wdm-order-csv-v1';
  policyVersion: 'WDM_ORDER_CLEANING_V1'; rawSha256: string; rawSizeBytes: string; completedAt: string;
  validationStatus: 'VALID' | 'INVALID';
  cleaningOutcome: 'READY' | 'EMPTY_INPUT' | 'EMPTY_AFTER_CLEANING' | 'BLOCKED_UNATTRIBUTABLE_ORDER' | 'NOT_RUN_STRUCTURAL_ERROR';
  countsComplete: boolean; counts: ValidationCounts;
  issues: { content: ValidationDiagnostic[]; page: number; size: number; totalElements: string; totalPages: string };
}
const fields = ['order_id', 'order_item_id', 'customer_code', 'ordered_at', 'status', 'product_id', 'sku_snapshot',
  'product_name_snapshot', 'quantity', 'list_unit_price', 'allocated_discount_amount', 'line_total', 'currency_code'];
function object(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}
function count(value: unknown): value is string { return typeof value === 'string' && /^(0|[1-9][0-9]*)$/.test(value); }
function positive(value: unknown): value is number { return Number.isSafeInteger(value) && (value as number) > 0; }
function position(value: unknown): value is ValidationPosition {
  return object(value) && positive(value.recordNumber) && positive(value.startLine) && positive(value.endLine)
    && value.endLine >= value.startLine;
}
function instant(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?Z$/.test(value)) return false;
  const parsed = new Date(value);
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 19) === value.slice(0, 19);
}
function diagnostic(value: unknown): value is ValidationDiagnostic {
  return object(value) && ['STRUCTURE', 'BUSINESS', 'CONSISTENCY', 'CLEANING'].includes(value.stage as string)
    && typeof value.code === 'string' && /^[A-Z][A-Z0-9_]{0,79}$/.test(value.code)
    && (value.field === null || fields.includes(value.field as string))
    && ['DECODER_CURSOR', 'RECORD_RANGE'].includes(value.positionAccuracy as string)
    && Array.isArray(value.positions) && value.positions.every(position);
}
function invalid(): never {
  throw new WdmApiError('response', 200, 'INVALID_VALIDATION_RESPONSE', 'Validation report could not be verified.');
}

/** Project only public fields; no raw values, private manifest or unsafe integer coercion. */
export function readValidationReport(value: unknown, id: string, page: number, size: number): ValidationReport {
  if (!object(value)) return invalid();
  const d = value;
  if (d.snapshotId !== id || d.reportVersion !== 'WDM_VALIDATION_REPORT_V1' || d.contractVersion !== 'wdm-order-csv-v1'
    || d.policyVersion !== 'WDM_ORDER_CLEANING_V1' || typeof d.rawSha256 !== 'string' || !/^[0-9a-f]{64}$/.test(d.rawSha256)
    || !count(d.rawSizeBytes) || d.rawSizeBytes === '0' || !instant(d.completedAt)
    || !['VALID', 'INVALID'].includes(d.validationStatus as string)
    || !['READY', 'EMPTY_INPUT', 'EMPTY_AFTER_CLEANING', 'BLOCKED_UNATTRIBUTABLE_ORDER', 'NOT_RUN_STRUCTURAL_ERROR'].includes(d.cleaningOutcome as string)
    || typeof d.countsComplete !== 'boolean' || !object(d.counts) || !object(d.issues)) return invalid();
  if ((d.validationStatus === 'VALID') !== (d.cleaningOutcome === 'READY')
    || d.countsComplete === (d.cleaningOutcome === 'NOT_RUN_STRUCTURAL_ERROR')) return invalid();
  const counts: ValidationCounts = {} as ValidationCounts;
  const sourceCounts = d.counts;
  for (const key of countKeys) {
    if (!(sourceCounts[key] === null || count(sourceCounts[key]))) return invalid();
    counts[key] = sourceCounts[key] as string | null;
  }
  if (!count(counts.parsedRecords) || !count(counts.issueCount)) return invalid();
  if (d.countsComplete && ['inputRecords', 'knownOrders', 'businessInvalidRecords', 'keptRecords', 'rejectedRecords',
    'deduplicatedRecords', 'blockedRecords', 'keptOrders'].some(key => !count(sourceCounts[key]))) return invalid();
  if (!d.countsComplete && countKeys.some(key => !['parsedRecords', 'issueCount'].includes(key) && sourceCounts[key] !== null)) return invalid();
  const issues = d.issues;
  if (issues.page !== page || issues.size !== size || !count(issues.totalElements) || !count(issues.totalPages)
    || issues.totalElements !== counts.issueCount || !Array.isArray(issues.content)
    || issues.content.length > size || !issues.content.every(diagnostic)
    || (issues.totalElements === '0' && (issues.totalPages !== '0' || issues.content.length !== 0))
    || (issues.totalElements !== '0' && issues.totalPages === '0')) return invalid();
  return { snapshotId: id, reportVersion: d.reportVersion, contractVersion: d.contractVersion, policyVersion: d.policyVersion,
    rawSha256: d.rawSha256, rawSizeBytes: d.rawSizeBytes, completedAt: d.completedAt,
    validationStatus: d.validationStatus as ValidationReport['validationStatus'], cleaningOutcome: d.cleaningOutcome as ValidationReport['cleaningOutcome'],
    countsComplete: d.countsComplete, counts, issues: { page, size, totalElements: issues.totalElements, totalPages: issues.totalPages,
      content: (issues.content as ValidationDiagnostic[]).map(issue => ({ stage: issue.stage, code: issue.code, field: issue.field,
        positionAccuracy: issue.positionAccuracy, positions: issue.positions.map(p => ({ recordNumber: p.recordNumber, startLine: p.startLine, endLine: p.endLine })) })) } };
}

/** Compare canonical decimal strings for paging without losing large-count precision. */
export function hasNextValidationPage(report: ValidationReport): boolean {
  const next = String(report.issues.page + 1), total = report.issues.totalPages;
  return next.length < total.length || (next.length === total.length && next < total);
}
