import { createWdmApiClient, WdmApiError } from './client';
import { WdmAuthAdapter } from './types';

let authAdapter: WdmAuthAdapter | null = null;

/** Shared Auth supplies this adapter after its carrier/lifecycle contract is confirmed. */
export function setWdmAuthAdapter(adapter: WdmAuthAdapter | null): void {
  authAdapter = adapter;
}

export const wdmApi = createWdmApiClient({
  baseUrl: process.env.REACT_APP_WDM_API_BASE_URL || '/api',
  getAuthAdapter: () => authAdapter,
});

export interface BackendHealth {
  status: 'UP';
  service: 'wspm-backend';
}

/** Existing public backend endpoint; no dependency on the future WDM/Admin endpoints. */
export async function getBackendHealth(): Promise<BackendHealth> {
  const health = await wdmApi.get<BackendHealth>('/health', { auth: false });
  if (!health || health.status !== 'UP' || health.service !== 'wspm-backend') {
    throw new WdmApiError('response', 200, 'INVALID_HEALTH_RESPONSE', 'Unexpected backend health response.');
  }
  return health;
}

export { createWdmApiClient, WdmApiError } from './client';
export type { WdmCount, WdmId, WdmPage, WdmErrorResponse, WdmAuthAdapter } from './types';
