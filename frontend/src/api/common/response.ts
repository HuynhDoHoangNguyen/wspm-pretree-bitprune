export interface ApiResponse<T> {
  code: 'SUCCESS';
  message: string;
  data: T;
  requestId: string;
}

export interface FieldErrorDetail { field: string; code: string; message: string }

export interface ApiErrorResponse {
  status: number;
  code: string;
  message: string;
  path: string;
  requestId: string;
  details: FieldErrorDetail[];
}

export function isApiErrorResponse(value: unknown, status: number): value is ApiErrorResponse {
  if (!value || typeof value !== 'object') return false;
  const error = value as ApiErrorResponse;
  return error.status === status && typeof error.code === 'string' && !!error.code
    && typeof error.message === 'string' && typeof error.path === 'string'
    && typeof error.requestId === 'string' && !!error.requestId && Array.isArray(error.details)
    && error.details.every(detail => detail && typeof detail.field === 'string'
      && typeof detail.code === 'string' && typeof detail.message === 'string');
}

/** No legacy fallback: endpoint-specific validation follows transport validation. */
export function unwrapApiResponse<T>(value: unknown, requestId: string | null): T {
  if (!value || typeof value !== 'object') throw new Error('INVALID_API_RESPONSE');
  const result = value as Partial<ApiResponse<T>>;
  const keys = Object.keys(result).sort().join(',');
  if (keys !== 'code,data,message,requestId' || result.code !== 'SUCCESS'
    || result.message !== 'Request completed successfully.'
    || typeof result.requestId !== 'string' || !result.requestId.trim()
    || !requestId || result.requestId !== requestId) {
    throw new Error('INVALID_API_RESPONSE');
  }
  return result.data as T;
}
