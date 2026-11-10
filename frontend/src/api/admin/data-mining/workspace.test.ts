import { readCapacity, readStorage, readWorkspacePage } from './workspace';
const id = '10000000-0000-4000-8000-000000000001', time = '2026-11-07T00:00:00Z';
test('capacity must be a coherent instance snapshot, not remaining DB writes', () => {
  const value = { enabled: true, scope: 'CURRENT_BACKEND_INSTANCE', observedAt: time,
    snapshot: { capacity: 2, used: 1, available: 1, closing: false, observedAt: time, entries: [{ runId: id, state: 'COMPLETED' }] } };
  expect(readCapacity(value)).toEqual(value);
  expect(readCapacity({ ...value, observedAt: '2026-11-07T00:00:00.123456789Z', snapshot: { ...value.snapshot, observedAt: '2026-11-07T00:00:00.123456789Z' } }).enabled).toBe(true);
  expect(() => readCapacity({ ...value, snapshot: { ...value.snapshot, available: 2 } })).toThrow();
  expect(() => readCapacity({ ...value, snapshot: { ...value.snapshot, entries: [] } })).toThrow();
  expect(readCapacity({ enabled: false, scope: value.scope, observedAt: time, snapshot: null }).enabled).toBe(false);
  expect(() => readCapacity({ ...value, enabled: false })).toThrow();
});
test('lists reject malformed identities and preserve exact count strings/nulls', () => {
  const row = { runId: id, snapshotId: id, sourceName: 'orders.csv', algorithm: 'WSPM_PRETREE', status: 'SUCCEEDED', minsupMode: 'ABSOLUTE', configuredValue: 0.1,
    patternCount: '18446744073709551615', createdAt: time, startedAt: time, finishedAt: time };
  const page = { items: [row], page: 1, size: 20, total: 1 };
  expect(readWorkspacePage(page, true)).toEqual(page);
  expect(() => readWorkspacePage({ ...page, items: [{ ...row, runId: 'bad' }] }, true)).toThrow();
  expect(() => readWorkspacePage({ ...page, size: 0 }, true)).toThrow();
  expect(() => readWorkspacePage({ ...page, items: [{ ...row, patternCount: 15 }] }, true)).toThrow();
});
test('storage binds to requested snapshot and labels paths as unverified metadata', () => {
  const value = { snapshotId: id, sourceName: 'orders.csv', root: 'C:\\snapshots', binding: 'CURRENT_SNAPSHOT_PUBLICATION', verification: 'METADATA_ONLY_NOT_INTEGRITY_VERIFIED',
    files: [{ kind: 'raw', filename: 'raw.csv', reference: id + '/raw.csv', absolutePath: 'C:\\snapshots\\' + id + '\\raw.csv', sha256: null }] };
  expect(readStorage(value, id)).toEqual(value);
  expect(() => readStorage({ ...value, binding: 'HISTORICAL_VERIFIED' }, id)).toThrow();
  expect(() => readStorage({ ...value, files: [...value.files, ...value.files] }, id)).toThrow();
});
test('V2 legacy paths and non-UUID identities remain discoverable only when explicitly unverified', () => {
  const row = { snapshotId: 'legacy-run-1', sourceName: 'old.csv', sourceType: 'LEGACY_PATH', validationStatus: 'LEGACY_UNVERIFIED', conversionStatus: null,
    sequenceCount: null, distinctItemCount: null, createdAt: time };
  const page = { items: [row], page: 1, size: 20, total: 1 };
  expect(readWorkspacePage(page, false)).toEqual(page);
  expect(() => readWorkspacePage({ ...page, items: [{ ...row, validationStatus: 'VALID' }] }, false)).toThrow();
  const run = { runId: 'legacy-run-1', snapshotId: row.snapshotId, sourceName: 'old.csv', algorithm: 'WSPM_PRETREE', status: 'LEGACY_UNVERIFIED', minsupMode: 'LEGACY_V1', configuredValue: 0,
    patternCount: null, createdAt: time, startedAt: null, finishedAt: null };
  expect(readWorkspacePage({ ...page, items: [run] }, true).items).toHaveLength(1);
});
