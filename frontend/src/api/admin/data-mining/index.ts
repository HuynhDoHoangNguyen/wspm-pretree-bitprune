import { createWdmApiClient, WdmApiError } from './client';
import { WdmAuthAdapter } from './types';
import { createDatasetApi } from './datasets';
import { createValidationApi } from './validation';
import { createConversionApi } from './conversion';
import { createMiningApi } from './mining';
import { createMiningRunApi } from './mining-runs';
import { createRunResultApi } from './run-results';

let authAdapter: WdmAuthAdapter | null = null;

/** Shared Auth supplies this adapter after its carrier/lifecycle contract is confirmed. */
export function setWdmAuthAdapter(adapter: WdmAuthAdapter | null): void {
  authAdapter = adapter;
}

export const wdmApi = createWdmApiClient({
  baseUrl: process.env.REACT_APP_WDM_API_BASE_URL || '/api',
  getAuthAdapter: () => authAdapter,
});

export const datasetApi = createDatasetApi(wdmApi);
export const validationApi = createValidationApi(wdmApi);
export const conversionApi = createConversionApi(wdmApi);
export const miningApi = createMiningApi(wdmApi);
export const miningRunApi = { create: createMiningRunApi(wdmApi).create };
export const runResultApi = createRunResultApi(wdmApi);
export type { MiningRunAccepted } from './mining-run-response';
export type { MiningRequest, MiningPreview, MiningValidation } from './mining-response';
export type { ConversionReport, ConversionArtifact, ConversionStatistics } from './conversion-response';
export type { ValidationReport, ValidationDiagnostic, ValidationPosition } from './validation-response';
export type { DatasetSnapshotResponse, SnapshotDetail } from './types';

export interface BackendHealth {
  status: 'UP';
  service: 'wspm-backend';
}

/** Existing public backend endpoint; no dependency on the future WDM/Admin endpoints. */
export async function getBackendHealth(): Promise<BackendHealth> {
  const health = await wdmApi.get<BackendHealth>('/health', { auth: false, responseMode: 'raw-health' });
  if (!health || health.status !== 'UP' || health.service !== 'wspm-backend') {
    throw new WdmApiError('response', 200, 'INVALID_HEALTH_RESPONSE', 'Unexpected backend health response.');
  }
  return health;
}

export { createWdmApiClient, WdmApiError } from './client';
export type { WdmCount, WdmId, WdmPage, WdmErrorResponse, WdmAuthAdapter } from './types';
