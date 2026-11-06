import { isApiErrorResponse, unwrapApiResponse } from 'api/common/response';
import { WdmAuthAdapter, WdmQuery, WdmValidationDetail } from './types';

export class WdmApiError extends Error {
  constructor(
    public readonly kind: 'http' | 'network' | 'response' | 'aborted',
    public readonly status: number | null,
    public readonly code: string,
    message: string,
    public readonly requestId: string | null = null,
    public readonly details: WdmValidationDetail[] = []
  ) {
    super(message);
    Object.setPrototypeOf(this, new.target.prototype);
    this.name = 'WdmApiError';
  }
}

export interface WdmRequestOptions {
  expectedStatus?: number;
  responseMode?: 'envelope' | 'raw-health';
  query?: WdmQuery;
  signal?: AbortSignal;
  /** Public endpoints such as health deliberately do not send the Admin token. */
  auth?: boolean;
  /** Bounded artifact reads must use a stream; no unbounded blob fallback. */
  maxDownloadBytes?: number;
  /** Endpoint decoder runs with the transport request ID still available. */
  decodeResponse?: (value: unknown) => unknown;
}

interface ClientOptions {
  /** API root, e.g. /api or https://backend.example/api. Not an endpoint URL. */
  baseUrl?: string;
  getAuthAdapter?: () => WdmAuthAdapter | null;
  fetchImpl?: typeof fetch;
}

export interface WdmDownload {
  blob: Blob;
  contentType: string | null;
  contentDisposition: string | null;
  requestId: string | null;
}

export function normalizeWdmBaseUrl(value = '/api'): string {
  const base = value.trim().replace(/\/+$/, '');
  if (!base || /[?#\\]/.test(base) || /\s/.test(base)) {
    throw new Error('WDM API base URL must be an API root without query or fragment.');
  }
  const url = new URL(base, 'http://wdm.local');
  if ((!base.startsWith('/') && !/^https?:\/\//.test(base)) || base.startsWith('//')
    || !['http:', 'https:'].includes(url.protocol) || url.username || url.password
    || !url.pathname.endsWith('/api')) {
    throw new Error('WDM API base URL must be an HTTP(S) URL or relative path ending in /api.');
  }
  return base;
}

function notifyAuth(adapter: WdmAuthAdapter | null, status: number): void {
  // UI/auth callbacks must not hide the original HTTP denial or trigger request replay.
  try {
    if (status === 401) adapter?.onUnauthorized?.();
    if (status === 403) adapter?.onForbidden?.();
  } catch {
    // The structured HTTP error remains authoritative for the caller.
  }
}

export function createWdmApiClient(options: ClientOptions = {}) {
  const baseUrl = normalizeWdmBaseUrl(options.baseUrl);

  function urlFor(path: string, query?: WdmQuery): string {
    if (!path.startsWith('/') || path.startsWith('//') || /[?#\\]/.test(path)
      || path.split('/').some(segment => ['.', '..'].includes(decodeURIComponent(segment)))) {
      throw new Error('WDM endpoint must be an API-root-relative path; pass query separately.');
    }
    const params = new URLSearchParams();
    Object.entries(query || {}).forEach(([key, value]) => {
      if (value == null) return;
      const values = Array.isArray(value) ? value : [value];
      values.forEach(item => params.append(key, String(item)));
    });
    const suffix = params.toString();
    return baseUrl + path + (suffix ? '?' + suffix : '');
  }

  async function request<T>(
    path: string,
    method: 'GET' | 'POST',
    body: unknown,
    requestOptions: WdmRequestOptions,
    download = false
  ): Promise<T> {
    if (requestOptions.responseMode === 'raw-health' && (path !== '/health' || method !== 'GET')) {
      throw new Error('Raw JSON mode is restricted to GET /health.');
    }
    const url = urlFor(path, requestOptions.query);
    const adapter = requestOptions.auth === false ? null : options.getAuthAdapter?.() || null;
    const sessionSignal = adapter?.getSessionSignal?.();
    const controller = new AbortController();
    const abort = () => controller.abort();
    const signals = [requestOptions.signal, sessionSignal].filter((signal): signal is AbortSignal => !!signal);
    signals.forEach(signal => { if (signal.aborted) abort(); else signal.addEventListener('abort', abort); });
    const token = adapter?.getAccessToken()?.trim();
    const headers = new Headers({ Accept: download ? '*/*' : 'application/json' });
    if (token) headers.set('Authorization', 'Bearer ' + token);
    const multipart = typeof FormData !== 'undefined' && body instanceof FormData;
    if (body !== undefined && !multipart) headers.set('Content-Type', 'application/json');
    const payload = body === undefined ? undefined : multipart ? body as FormData : JSON.stringify(body);

    try {
      const response = await (options.fetchImpl || fetch)(url, {
        method, headers, body: payload, signal: controller.signal, credentials: 'omit',
      });
      const requestId = response.headers.get('X-Request-Id');
      if (controller.signal.aborted) {
        await response.body?.cancel();
        throw new WdmApiError('aborted', null, 'REQUEST_ABORTED', 'API request was cancelled.');
      }
      if (response.ok && requestOptions.expectedStatus !== undefined && response.status !== requestOptions.expectedStatus) {
        throw new WdmApiError('response', response.status, 'INVALID_RESPONSE', 'API returned an unexpected success status.', requestId);
      }
      if (response.ok && download) {
        let blob: Blob;
        const limit = requestOptions.maxDownloadBytes;
        if (limit !== undefined) {
          const tooLarge = () => new WdmApiError('response', 200, 'DOWNLOAD_LIMIT_EXCEEDED', 'Artifact exceeds the download limit.', requestId);
          const declared = response.headers.get('Content-Length');
          if (!Number.isSafeInteger(limit) || limit < 1) throw tooLarge();
          if (declared && (!/^(0|[1-9][0-9]*)$/.test(declared) || declared.length > String(limit).length
            || (declared.length === String(limit).length && declared > String(limit)))) {
            await response.body?.cancel();
            throw tooLarge();
          }
          if (!response.body) throw new WdmApiError('response', 200, 'INVALID_DOWNLOAD_STREAM', 'Artifact stream is unavailable.', requestId);
          const reader = response.body.getReader();
          const cancel = () => { void reader.cancel().catch(() => undefined); };
          controller.signal.addEventListener('abort', cancel);
          const chunks: Uint8Array[] = [];
          let size = 0;
          try {
            for (let next = await reader.read(); !next.done; next = await reader.read()) {
              if (controller.signal.aborted) throw new WdmApiError('aborted', null, 'REQUEST_ABORTED', 'API request was cancelled.');
              size += next.value.byteLength;
              if (size > limit) { await reader.cancel(); throw tooLarge(); }
              chunks.push(next.value);
            }
            blob = new Blob(chunks, { type: response.headers.get('Content-Type') || '' });
          } finally {
            controller.signal.removeEventListener('abort', cancel);
            reader.releaseLock();
          }
        } else blob = await response.blob();
        if (controller.signal.aborted) throw new WdmApiError('aborted', null, 'REQUEST_ABORTED', 'API request was cancelled.');
        return {
          blob,
          contentType: response.headers.get('Content-Type'),
          contentDisposition: response.headers.get('Content-Disposition'),
          requestId,
        } as unknown as T;
      }
      if (response.ok && response.status === 204) return undefined as unknown as T;

      const text = await response.text();
      if (response.ok && controller.signal.aborted) throw new WdmApiError('aborted', null, 'REQUEST_ABORTED', 'API request was cancelled.');
      const jsonContent = /\bapplication\/(?:[\w.-]+\+)?json\b/i.test(response.headers.get('Content-Type') || '');
      let data: unknown;
      if (jsonContent) {
        try { data = JSON.parse(text); } catch { /* handled below without leaking the raw body */ }
      }
      if (!response.ok) {
        notifyAuth(adapter, response.status); // Parse first so session abort does not hide the original denial.
        if (isApiErrorResponse(data, response.status)) {
          throw new WdmApiError('http', response.status, data.code, data.message,
            requestId || data.requestId, data.details);
        }
        throw new WdmApiError('http', response.status, 'HTTP_ERROR',
          'API request failed (HTTP ' + response.status + ').', requestId);
      }
      if (!jsonContent || data === undefined) {
        throw new WdmApiError('response', response.status, 'INVALID_RESPONSE',
          'API returned an invalid JSON response.', requestId);
      }
      if (requestOptions.responseMode === 'raw-health') return data as T;
      let decoded: unknown;
      try { decoded = unwrapApiResponse<unknown>(data, requestId); }
      catch { throw new WdmApiError('response', response.status, 'INVALID_RESPONSE', 'API returned an invalid response envelope.', requestId); }
      try { return (requestOptions.decodeResponse ? requestOptions.decodeResponse(decoded) : decoded) as T; }
      catch (error) {
        if (error instanceof WdmApiError) throw new WdmApiError(error.kind, error.status, error.code, error.message, requestId, error.details);
        throw new WdmApiError('response', response.status, 'INVALID_RESPONSE', 'API response could not be verified.', requestId);
      }
    } catch (error) {
      if (error instanceof WdmApiError) throw error;
      if (controller.signal.aborted || (error instanceof Error && error.name === 'AbortError')) {
        throw new WdmApiError('aborted', null, 'REQUEST_ABORTED', 'API request was cancelled.');
      }
      throw new WdmApiError('network', null, 'NETWORK_ERROR', 'Unable to connect to the API.');
    } finally {
      signals.forEach(signal => signal.removeEventListener('abort', abort));
    }
  }

  return {
    get: <T>(path: string, opts: WdmRequestOptions = {}) => request<T>(path, 'GET', undefined, opts),
    post: <T>(path: string, body?: unknown, opts: WdmRequestOptions = {}) => request<T>(path, 'POST', body, opts),
    download: (path: string, opts: WdmRequestOptions = {}) => request<WdmDownload>(path, 'GET', undefined, opts, true),
  };
}
