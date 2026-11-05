import { createWdmApiClient, WdmApiError } from './client';
import { isSnapshotId } from './dataset-response';
import { readValidationReport } from './validation-response';

export function createValidationApi(client: ReturnType<typeof createWdmApiClient>) {
  function path(id: string) {
    if (!isSnapshotId(id)) throw new WdmApiError('http', 400, 'VALIDATION_FAILED', 'Invalid snapshot ID.');
    return '/v1/admin/dataset-snapshots/' + id + '/validation';
  }
  return {
    async start(id: string, signal?: AbortSignal) {
      return readValidationReport(await client.post<unknown>(path(id), undefined, { signal, expectedStatus: 200 }), id, 0, 20);
    },
    async read(id: string, page = 0, size = 20, signal?: AbortSignal) {
      if (!Number.isInteger(page) || page < 0 || page > 2147483647 || !Number.isInteger(size) || size < 1 || size > 100) {
        throw new WdmApiError('http', 400, 'VALIDATION_FAILED', 'Invalid report page.');
      }
      return readValidationReport(await client.get<unknown>(path(id), { signal, expectedStatus: 200, query: { page, size } }), id, page, size);
    },
  };
}
