import { WdmApiError } from './client';
import { isSnapshotId } from './dataset-response';
import { ConversionStatistics, statisticKeys } from './conversion-response';

export interface MiningRequest { snapshotId: string; algorithm: 'WSPM_PRETREE' | 'WSPM_BITPRUNE'; minsupMode: 'PERCENT' | 'ABSOLUTE'; minsupValue: number }
export interface MiningSupport { mode: MiningRequest['minsupMode']; configuredValue: number; relativeFraction: number | null; effectiveThreshold: number }
export interface MiningBinding { snapshotId: string; algorithm: MiningRequest['algorithm']; artifactVersion: 'CONVERSION_ARTIFACT_V1'; sequenceSha256: string; mappingSha256: string; minimumSupport: MiningSupport }
export interface MiningPreview extends MiningBinding { statistics: ConversionStatistics; executionEligibility: 'NOT_EVALUATED' }
export interface MiningValidation extends MiningBinding { sequenceCount: string; configurationStatus: 'SUPPORTED' }
function object(v: unknown): v is Record<string, unknown> { return !!v && typeof v === 'object' && !Array.isArray(v); }
function finite(v: unknown): v is number { return typeof v === 'number' && Number.isFinite(v); }
function count(v: unknown): v is string { return typeof v === 'string' && v.trim() === v && /^(0|[1-9][0-9]*)$/.test(v); }
function invalid(): never { throw new WdmApiError('response', 200, 'INVALID_MINING_RESPONSE', 'Mining response could not be confirmed.'); }
function binding(value: unknown, request: MiningRequest): MiningBinding {
  if (!isSnapshotId(request.snapshotId) || request.snapshotId.length !== 36 || !object(value) || value.snapshotId !== request.snapshotId || value.algorithm !== request.algorithm
    || value.artifactVersion !== 'CONVERSION_ARTIFACT_V1' || typeof value.sequenceSha256 !== 'string' || value.sequenceSha256.length !== 64 || !/^[0-9a-f]{64}$/.test(value.sequenceSha256)
    || typeof value.mappingSha256 !== 'string' || value.mappingSha256.length !== 64 || !/^[0-9a-f]{64}$/.test(value.mappingSha256) || !object(value.minimumSupport)) return invalid();
  const s = value.minimumSupport;
  if (s.mode !== request.minsupMode || !finite(s.configuredValue) || s.configuredValue !== request.minsupValue || !finite(s.effectiveThreshold) || s.effectiveThreshold < 0
    || (request.minsupMode === 'ABSOLUTE' ? s.relativeFraction !== null : !finite(s.relativeFraction) || s.relativeFraction < 0 || s.relativeFraction > 1)) return invalid();
  return { snapshotId: request.snapshotId, algorithm: request.algorithm, artifactVersion: 'CONVERSION_ARTIFACT_V1',
    sequenceSha256: value.sequenceSha256, mappingSha256: value.mappingSha256,
    minimumSupport: { mode: request.minsupMode, configuredValue: s.configuredValue, relativeFraction: s.relativeFraction as number | null, effectiveThreshold: s.effectiveThreshold } };
}
export function readMiningPreview(value: unknown, request: MiningRequest): MiningPreview {
  const bound = binding(value, request);
  if (!object(value) || value.executionEligibility !== 'NOT_EVALUATED' || !object(value.statistics)) return invalid();
  const s = value.statistics;
  if (!statisticKeys.every(key => count(s[key])) || !finite(s.averageSequenceLength) || s.averageSequenceLength < 0) return invalid();
  const statistics = { averageSequenceLength: s.averageSequenceLength } as ConversionStatistics;
  statisticKeys.forEach(key => { statistics[key] = s[key] as string; });
  return { ...bound, statistics, executionEligibility: 'NOT_EVALUATED' };
}
export function readMiningValidation(value: unknown, request: MiningRequest): MiningValidation {
  const bound = binding(value, request);
  if (!object(value) || value.configurationStatus !== 'SUPPORTED' || !count(value.sequenceCount)) return invalid();
  return { ...bound, sequenceCount: value.sequenceCount, configurationStatus: 'SUPPORTED' };
}
/** Compare server attestations only; never recompute mining formulas in TypeScript. */
export function sameMiningInput(preview: MiningPreview, validation: MiningValidation): boolean {
  return preview.snapshotId === validation.snapshotId && preview.algorithm === validation.algorithm
    && preview.artifactVersion === validation.artifactVersion && preview.sequenceSha256 === validation.sequenceSha256
    && preview.mappingSha256 === validation.mappingSha256 && preview.statistics.sequenceCount === validation.sequenceCount
    && preview.minimumSupport.mode === validation.minimumSupport.mode && preview.minimumSupport.configuredValue === validation.minimumSupport.configuredValue
    && preview.minimumSupport.relativeFraction === validation.minimumSupport.relativeFraction
    && preview.minimumSupport.effectiveThreshold === validation.minimumSupport.effectiveThreshold;
}
