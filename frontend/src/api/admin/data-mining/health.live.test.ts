/** @jest-environment node */
import { runInThisContext } from 'vm';

Object.assign(globalThis, runInThisContext('({ fetch, Headers, Response, FormData, Blob })'));

// Opt in explicitly: this test calls an actual Spring HTTP endpoint; no fetch mock.
const liveBaseUrl = process.env.WDM_LIVE_API_BASE_URL;
const liveTest = liveBaseUrl ? test : test.skip;

liveTest('shared WDM client connects to the real backend health endpoint', async () => {
  process.env.REACT_APP_WDM_API_BASE_URL = liveBaseUrl;
  const { getBackendHealth, setWdmAuthAdapter } = await import('./index');
  setWdmAuthAdapter({ getAccessToken: () => { throw new Error('Public health must not read Auth credentials'); } });
  try {
    await expect(getBackendHealth()).resolves.toEqual({ status: 'UP', service: 'wspm-backend' });
  } finally {
    setWdmAuthAdapter(null);
  }
}, 15000);

export {};
