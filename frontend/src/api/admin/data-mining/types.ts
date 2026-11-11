import { ApiErrorResponse, FieldErrorDetail } from 'api/common/response';

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

export type WdmValidationDetail = FieldErrorDetail;

export type WdmErrorResponse = ApiErrorResponse;

export interface WdmAuthAdapter {
  getAccessToken: () => string | null | undefined;
  /** Read at request start; the shared session aborts/replaces this signal on identity invalidation. */
  getSessionSignal?: () => AbortSignal;
  onUnauthorized?: () => void;
  onForbidden?: () => void;
}

type QueryValue = string | number | boolean;
export type WdmQuery = Record<string, QueryValue | readonly QueryValue[] | null | undefined>;

export interface DatasetSnapshotResponse {
  snapshotId: WdmId;
  sourceType: 'CSV';
  sourceName: string;
  validationStatus: 'PENDING';
  conversionStatus: 'NOT_STARTED';
  statisticsStatus: 'NOT_COMPUTED';
  rawSha256: string;
  rawSizeBytes: WdmCount;
  sequenceCount: null;
  fromTime: null;
  toTime: null;
  createdAt: string;
}

export interface SnapshotDetail {
  snapshotId: string;
  sourceType: 'CSV' | 'DATABASE' | null;
  sourceName: string | null;
  validationStatus: 'PENDING' | 'VALID' | 'INVALID' | 'LEGACY_UNVERIFIED' | null;
  conversionStatus: 'NOT_STARTED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | null;
  statisticsStatus: 'NOT_COMPUTED' | 'COMPUTED' | null;
  rawSha256: string | null;
  rawSizeBytes: string | null;
  sequenceCount: string | null;
  fromTime: string | null;
  toTime: string | null;
  createdAt: string;
  rawAvailability: 'AVAILABLE' | 'MISSING' | 'UNAVAILABLE';
  provenance: {
    contractVersion: 'wdm-order-csv-v1' | null;
    acquisition: 'CSV_UPLOAD' | 'DATABASE_EXPORT' | null;
    sourceTimeZone: 'UTC' | null;
    timeField: 'ordered_at' | null;
    statusPolicy: 'COMPLETED_CURRENT_AT_ACQUISITION' | null;
  };
}
