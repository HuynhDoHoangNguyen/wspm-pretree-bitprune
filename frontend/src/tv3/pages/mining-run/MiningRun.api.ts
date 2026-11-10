import { wdmApi, WdmApiError } from 'api/admin/data-mining';
import { authSession } from 'auth/session';
import { MiningRunDetail, MiningRunPage, MiningRunParams } from './MiningRun.types';

const endpoint = '/v1/admin/mining-runs';

// Keep the existing UI error type while retaining shared transport correlation/details.
export class MiningRunApiError extends WdmApiError {
  constructor(status: number, original?: WdmApiError) {
    super(original?.kind || 'http', status, original?.code || 'HTTP_ERROR',
      'Unable to load Mining RUN data.', original?.requestId, original?.details);
    this.name = 'MiningRunApiError';
  }
}

async function get<T>(path: string, options: Parameters<typeof wdmApi.get>[1]): Promise<T> {
  const state = authSession.useStore.getState();
  if (state.status !== 'authenticated' || !authSession.token()) throw new MiningRunApiError(401);
  if (state.forbidden || !state.user?.roles.includes('ADMIN')) throw new MiningRunApiError(403);
  try {
    // AuthBoundary supplies the shared adapter: latest token, session abort and 401/403 callbacks.
    // wdmApi uses the common response parser; no raw DTO or second unwrap here.
    return await wdmApi.get<T>(path, { ...options, expectedStatus: 200 });
  } catch (error) {
    if (error instanceof WdmApiError && error.status !== null) throw new MiningRunApiError(error.status, error);
    throw error;
  }
}

export function listMiningRuns(params: MiningRunParams, signal?: AbortSignal) {
  return get<MiningRunPage>(endpoint, {
    signal,
    query: { page: params.page, size: params.size,
      ...(params.algorithm ? { algorithm: params.algorithm } : {}),
      ...(params.status ? { status: params.status } : {}),
    },
  });
}

export function getMiningRun(runId: string, signal?: AbortSignal) {
  return get<MiningRunDetail>(endpoint + '/' + encodeURIComponent(runId), { signal });
}
