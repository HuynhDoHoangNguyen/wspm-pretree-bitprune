import { createWdmApiClient, WdmApiError } from './client';
import { isSnapshotId } from './dataset-response';
import { MiningRequest, MiningPreview, MiningValidation, readMiningPreview, readMiningValidation } from './mining-response';

export function createMiningApi(client: ReturnType<typeof createWdmApiClient>) {
  function payload(input: MiningRequest): MiningRequest {
    if (!isSnapshotId(input.snapshotId) || input.snapshotId.length !== 36 || !['WSPM_PRETREE', 'WSPM_BITPRUNE'].includes(input.algorithm)
      || !['PERCENT', 'ABSOLUTE'].includes(input.minsupMode) || !Number.isFinite(input.minsupValue) || input.minsupValue < 0
      || (input.minsupMode === 'PERCENT' && input.minsupValue > 100)) throw new WdmApiError('http', 400, 'VALIDATION_FAILED', 'Supply a valid mining configuration.');
    return { snapshotId: input.snapshotId, algorithm: input.algorithm, minsupMode: input.minsupMode, minsupValue: input.minsupValue };
  }
  return {
    preview(input: MiningRequest, signal?: AbortSignal) {
      const request = payload(input);
      return client.post<MiningPreview>('/v1/admin/mining-runs/preview', request, { signal, expectedStatus: 200, decodeResponse: v => readMiningPreview(v, request) });
    },
    validate(input: MiningRequest, signal?: AbortSignal) {
      const request = payload(input);
      return client.post<MiningValidation>('/v1/admin/mining-runs/configuration-validation', request, { signal, expectedStatus: 200, decodeResponse: v => readMiningValidation(v, request) });
    },
  };
}
