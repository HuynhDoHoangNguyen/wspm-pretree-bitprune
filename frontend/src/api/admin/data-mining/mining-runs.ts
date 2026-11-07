import { createWdmApiClient, WdmApiError } from './client';
import { MiningRequest } from './mining-response';
import { isRunId, readMiningRunAccepted, readMiningRunStatus } from './mining-run-response';

export function createMiningRunApi(client: ReturnType<typeof createWdmApiClient>) {
  return {
    create(input: MiningRequest, signal?: AbortSignal) {
      if (!isRunId(input.snapshotId) || !['WSPM_PRETREE', 'WSPM_BITPRUNE'].includes(input.algorithm) || !['PERCENT', 'ABSOLUTE'].includes(input.minsupMode)
        || !Number.isFinite(input.minsupValue) || input.minsupValue < 0 || (input.minsupMode === 'PERCENT' && input.minsupValue > 100))
        throw new WdmApiError('http', 400, 'VALIDATION_FAILED', 'Supply a valid mining configuration.');
      const request: MiningRequest = { snapshotId: input.snapshotId, algorithm: input.algorithm, minsupMode: input.minsupMode, minsupValue: input.minsupValue };
      return client.post('/v1/admin/mining-runs', request, { signal, expectedStatus: 202, decodeResponse: v => readMiningRunAccepted(v, request) }) as Promise<ReturnType<typeof readMiningRunAccepted>>;
    },
    read(id: string, signal?: AbortSignal) {
      if (!isRunId(id)) throw new WdmApiError('http', 400, 'VALIDATION_FAILED', 'Supply a canonical run UUID.');
      return client.get('/v1/admin/mining-runs/' + id, { signal, expectedStatus: 200, decodeResponse: v => readMiningRunStatus(v, id) }) as Promise<ReturnType<typeof readMiningRunStatus>>;
    },
  };
}
