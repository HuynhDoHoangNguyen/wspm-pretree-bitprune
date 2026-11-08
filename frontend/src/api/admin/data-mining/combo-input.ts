import { createWdmApiClient, WdmApiError } from './client';
import { isRunId } from './mining-run-response';
import { wdmApi } from './index';

export interface ComboInputSummary {
  runId: string; snapshotId: string; publicationDigest: string;
  patternCount: string; dictionaryCount: string; inputStatus: 'VERIFIED_PUBLISHED_INPUT';
  generationStatus: 'BLOCKED' | 'READY_FOR_PREVIEW'; blockers: string[];
}
export function readComboInput(value: unknown, runId: string): ComboInputSummary {
  const v = value as Record<string, unknown> | null;
  const count = (n: unknown): n is string => typeof n === 'string' && /^[1-9][0-9]{0,4}$/.test(n) && Number(n) <= 10000;
  if (!v || v.runId !== runId || !isRunId(runId) || typeof v.snapshotId !== 'string' || !isRunId(v.snapshotId)
    || typeof v.publicationDigest !== 'string' || !/^[0-9a-f]{64}$/.test(v.publicationDigest)
    || !count(v.patternCount) || !count(v.dictionaryCount) || v.inputStatus !== 'VERIFIED_PUBLISHED_INPUT'
    || !['BLOCKED', 'READY_FOR_PREVIEW'].includes(String(v.generationStatus)) || !Array.isArray(v.blockers) || v.blockers.length > 20
    || (v.generationStatus === 'BLOCKED' ? v.blockers.length < 1 : v.blockers.length !== 0)
    || v.blockers.some(b => typeof b !== 'string' || !/^[A-Z][A-Z0-9_]{0,100}$/.test(b))) {
    throw new WdmApiError('response', 200, 'INVALID_COMBO_INPUT', 'Combo input could not be verified.');
  }
  return { runId, snapshotId: v.snapshotId, publicationDigest: v.publicationDigest,
    patternCount: v.patternCount, dictionaryCount: v.dictionaryCount, inputStatus: v.inputStatus,
    generationStatus: v.generationStatus as ComboInputSummary['generationStatus'], blockers: [...v.blockers] };
}
export function createComboInputApi(client: ReturnType<typeof createWdmApiClient>) {
  return { read(runId: string, signal?: AbortSignal) {
    if (!isRunId(runId)) throw new WdmApiError('http', 400, 'VALIDATION_FAILED', 'Supply a canonical run UUID.');
    return client.get<ComboInputSummary>('/v1/admin/mining-runs/' + runId + '/combo-input',
      { signal, expectedStatus: 200, decodeResponse: value => readComboInput(value, runId) });
  } };
}
export const comboInputApi = createComboInputApi(wdmApi);
