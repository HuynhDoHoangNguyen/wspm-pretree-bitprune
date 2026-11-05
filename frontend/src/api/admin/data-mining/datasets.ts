import { createWdmApiClient, WdmApiError } from './client';
import { isSnapshotId, readSnapshotDetail, readSnapshotReceipt, snapshotUuidPattern as uuid } from './dataset-response';

export interface ExportPeriod { fromTime: string; toTime: string }
const exportName = new RegExp('^attachment;\\s*filename="?(order-export-' + uuid + '\\.csv)"?$', 'i');
const rawName = new RegExp('^attachment;\\s*filename="?(snapshot-' + uuid + '-raw\\.csv)"?$', 'i');

function identifier(id: string) {
  if (!isSnapshotId(id)) throw new WdmApiError('http', 400, 'VALIDATION_FAILED', 'Invalid snapshot ID.');
  return '/v1/admin/dataset-snapshots/' + id;
}

export function createDatasetApi(client: ReturnType<typeof createWdmApiClient>) {
  return {
    async detail(id: string, signal?: AbortSignal) {
      return readSnapshotDetail(await client.get<unknown>(identifier(id), { signal, expectedStatus: 200 }), id);
    },
    async downloadRaw(id: string, signal?: AbortSignal) {
      const result = await client.download(identifier(id) + '/artifacts/raw', { signal, expectedStatus: 200 });
      if (result.contentType?.split(';')[0].trim().toLowerCase() !== 'text/csv' || result.blob.size === 0) {
        throw new WdmApiError('response', 200, 'INVALID_CSV_RESPONSE', 'API did not return a raw CSV artifact.', result.requestId);
      }
      return { ...result, filename: result.contentDisposition?.match(rawName)?.[1] || 'snapshot-' + id + '-raw.csv' };
    },
    async exportOrders(period: ExportPeriod, signal?: AbortSignal) {
      const result = await client.download('/v1/admin/order-exports', { query: { ...period }, signal, expectedStatus: 200 });
      if (result.contentType?.split(';')[0].trim().toLowerCase() !== 'text/csv' || result.blob.size === 0) {
        throw new WdmApiError('response', 200, 'INVALID_CSV_RESPONSE', 'API did not return a CSV artifact.', result.requestId);
      }
      return { ...result, filename: result.contentDisposition?.match(exportName)?.[1] || 'order-export.csv' };
    },
    async upload(file: File, signal?: AbortSignal) {
      const body = new FormData();
      body.append('file', file, file.name);
      return readSnapshotReceipt(await client.post<unknown>('/v1/admin/dataset-snapshots', body, { signal, expectedStatus: 201 }));
    },
  };
}
