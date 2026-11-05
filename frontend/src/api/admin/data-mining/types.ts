/** V2 BIGINT IDs/counts stay strings on the wire; never coerce them to Number here. */
export type WdmId = string;
export type WdmCount = string;

export interface WdmPage<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: WdmCount;
  hasNext: boolean;
  sort: Array<{ field: string; direction: 'ASC' | 'DESC' }>;
}

export interface WdmValidationDetail {
  field: string;
  code: string;
  message: string;
}

export interface WdmErrorResponse {
  status: number;
  code: string;
  message: string;
  path: string;
  requestId: string;
  details: WdmValidationDetail[];
}

export interface WdmAuthAdapter {
  getAccessToken: () => string | null | undefined;
  onUnauthorized?: () => void;
  onForbidden?: () => void;
}

type QueryValue = string | number | boolean;
export type WdmQuery = Record<string, QueryValue | readonly QueryValue[] | null | undefined>;
