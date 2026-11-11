import { createWdmApiClient, WdmApiError } from './client';
import { isSnapshotId } from './dataset-response';
import { ConversionArtifact, ConversionReport, readConversionReport } from './conversion-response';

export const PREVIEW_MAX_BYTES = 1024 * 1024;
export function previewAllowed(size: string): boolean {
  return /^(0|[1-9][0-9]*)$/.test(size) && (size.length < 7 || (size.length === 7 && size <= String(PREVIEW_MAX_BYTES)));
}
export function createConversionApi(client: ReturnType<typeof createWdmApiClient>) {
  function path(id: string) {
    if (!isSnapshotId(id)) throw new WdmApiError('http', 400, 'VALIDATION_FAILED', 'Invalid snapshot ID.');
    return '/v1/admin/dataset-snapshots/' + id;
  }
  async function download(id: string, kind: 'sequence' | 'mapping', expected: ConversionArtifact, signal?: AbortSignal, preview = false) {
    const base = path(id), filename = 'snapshot-' + id + (kind === 'sequence' ? '.spmf' : '-mapping.json');
    if (expected.downloadUrl !== '/api' + base + '/artifacts/' + kind || !/^[1-9][0-9]*$/.test(expected.sizeBytes)
      || (preview && !previewAllowed(expected.sizeBytes))) throw new WdmApiError('response', null, 'INVALID_ARTIFACT_METADATA', 'Artifact metadata could not be verified.');
    // Construct the authenticated request locally; never follow a response-provided URL.
    const result = await client.download(base + '/artifacts/' + kind, { signal, expectedStatus: 200,
      maxDownloadBytes: preview ? PREVIEW_MAX_BYTES : undefined });
    const mime = result.contentType?.split(';')[0].trim().toLowerCase();
    const disposition = result.contentDisposition;
    if (mime !== (kind === 'sequence' ? 'text/plain' : 'application/json') || String(result.blob.size) !== expected.sizeBytes
      || (disposition !== 'attachment; filename="' + filename + '"' && disposition !== 'attachment; filename=' + filename)) {
      throw new WdmApiError('response', 200, 'INVALID_CONVERSION_ARTIFACT', 'Artifact response could not be verified.', result.requestId);
    }
    return { ...result, filename };
  }
  return {
    async read(id: string, signal?: AbortSignal) {
      return client.get<ConversionReport>(path(id) + '/conversion', { signal, expectedStatus: 200, decodeResponse: value => readConversionReport(value, id) });
    },
    async start(id: string, signal?: AbortSignal) {
      return client.post<ConversionReport>(path(id) + '/conversion', undefined, { signal, expectedStatus: 200, decodeResponse: value => {
        const report = readConversionReport(value, id);
        if (report.conversionStatus !== 'SUCCEEDED') throw new WdmApiError('response', 200, 'INVALID_CONVERSION_RESPONSE', 'POST did not confirm a completed conversion.');
        return report;
      } });
    },
    download,
    async preview(id: string, expected: ConversionArtifact, signal?: AbortSignal) {
      const result = await download(id, 'sequence', expected, signal, true);
      const text = await result.blob.text();
      if (signal?.aborted) throw new WdmApiError('aborted', null, 'REQUEST_ABORTED', 'Preview was cancelled.');
      const lines = text.split('\n');
      let prefix = '';
      for (let index = 0; index < Math.min(20, lines.length); index++) {
        const line = lines[index] + (index < lines.length - 1 ? '\n' : '');
        if (prefix.length + line.length > 4000) break;
        prefix += line;
      }
      return { text: prefix, truncated: prefix.length < text.length };
    },
  };
}
