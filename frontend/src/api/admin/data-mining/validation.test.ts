/** @jest-environment node */
import { runInThisContext } from 'vm';
import { createWdmApiClient } from './client';
import { createValidationApi } from './validation';
import { hasNextValidationPage, readValidationReport } from './validation-response';
import { reportData, validationId as id } from './validation-test-data';

Object.assign(globalThis, runInThisContext('({ Headers, Response })'));
const response = (data: unknown) => new Response(JSON.stringify({ code: 'SUCCESS', message: 'Request completed successfully.', data, requestId: 'req-1' }),
  { headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'req-1' } });

it('uses bodyless POST and authenticated paged GET without replay or numeric coercion', async () => {
  const fetchMock = jest.fn().mockResolvedValue(response(reportData()));
  const api = createValidationApi(createWdmApiClient({ fetchImpl: fetchMock, getAuthAdapter: () => ({ getAccessToken: () => 'token' }) }));
  await expect(api.start(id)).resolves.toEqual(reportData());
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/dataset-snapshots/' + id + '/validation');
  expect(fetchMock.mock.calls[0][1].body).toBeUndefined();
  expect(fetchMock.mock.calls[0][1].headers.has('Content-Type')).toBe(false);
  expect(fetchMock.mock.calls[0][1].headers.get('Authorization')).toBe('Bearer token');
  fetchMock.mockResolvedValue(response(reportData()));
  await api.read(id);
  expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/admin/dataset-snapshots/' + id + '/validation?page=0&size=20');
  expect(fetchMock.mock.calls[1][1].method).toBe('GET');
  expect(fetchMock).toHaveBeenCalledTimes(2);
});

it.each([['bad', 0, 20], [id, -1, 20], [id, 2147483648, 20], [id, 0, 0], [id, 0, 101], [id, 0.5, 20]])(
  'rejects invalid ID/page/size before fetch (%s %s %s)', async (key, page, size) => {
    const fetchMock = jest.fn();
    const api = createValidationApi(createWdmApiClient({ fetchImpl: fetchMock }));
    await expect(api.read(key as string, page as number, size as number)).rejects.toMatchObject({ status: 400 });
    expect(fetchMock).not.toHaveBeenCalled();
  });

it.each([
  { reportVersion: 'unsupported' }, { policyVersion: 'unsupported' }, { snapshotId: 'wrong' },
  { completedAt: '2026-02-30T00:00:00Z' }, { validationStatus: 'PENDING' }, { cleaningOutcome: 'EMPTY_INPUT' },
  { countsComplete: false }, { rawSha256: 'bad' }, { rawSizeBytes: 900 },
  { counts: { ...reportData().counts, inputRecords: 5 } }, { counts: { ...reportData().counts, keptRecords: null } },
  { issues: { ...reportData().issues, page: 1 } }, { issues: { ...reportData().issues, totalElements: '4' } },
  { issues: { ...reportData().issues, content: [{ ...reportData().issues.content[0], field: 'private-value' }] } },
  { issues: { ...reportData().issues, content: [{ ...reportData().issues.content[0], code: '<script>' }] } },
  { issues: { ...reportData().issues, content: [{ ...reportData().issues.content[0], positions: [{ recordNumber: 9007199254740992, startLine: 2, endLine: 2 }] }] } },
])('rejects unsupported or malformed wire data %j', patch => {
  expect(() => readValidationReport({ ...reportData(), ...patch }, id, 0, 20)).toThrow('could not be verified');
});

it('preserves exact counters, microseconds and only projects public fields', () => {
  const raw = { ...reportData(), manifest: [{ raw: 'secret' }], path: 'private', counts: { ...reportData().counts, inputRecords: '9007199254740993' } };
  const report = readValidationReport(raw, id, 0, 20);
  expect(report.counts.inputRecords).toBe('9007199254740993');
  expect(report.completedAt).toBe('2026-11-05T01:02:03.123456Z');
  expect(JSON.stringify(report)).not.toContain('secret');
  expect(JSON.stringify(report)).not.toContain('private');
  expect(hasNextValidationPage(report)).toBe(false);
  expect(hasNextValidationPage({ ...report, issues: { ...report.issues, totalPages: '9007199254740993' } })).toBe(true);
});

it('accepts nullable incomplete prefix counts and an empty-position cleaning diagnostic', () => {
  const raw = reportData();
  raw.validationStatus = 'INVALID'; raw.cleaningOutcome = 'NOT_RUN_STRUCTURAL_ERROR'; raw.countsComplete = false;
  Object.keys(raw.counts).forEach(key => { (raw.counts as Record<string, unknown>)[key] = null; });
  raw.counts.parsedRecords = '5'; raw.counts.issueCount = '1';
  raw.issues = { ...raw.issues, totalElements: '1', content: [{ stage: 'STRUCTURE', code: 'CSV_INVALID_UTF8', field: null,
    positionAccuracy: 'DECODER_CURSOR', positions: [{ recordNumber: 1, startLine: 1, endLine: 1 }] }] };
  expect(readValidationReport(raw, id, 0, 20).counts.inputRecords).toBeNull();
  const empty = reportData(); empty.validationStatus = 'INVALID'; empty.cleaningOutcome = 'EMPTY_INPUT';
  empty.counts.issueCount = '1'; empty.issues.totalElements = '1';
  empty.issues.content = [{ stage: 'CLEANING', code: 'EMPTY_INPUT', field: null, positionAccuracy: 'RECORD_RANGE', positions: [] }];
  expect(readValidationReport(empty, id, 0, 20).cleaningOutcome).toBe('EMPTY_INPUT');
});

it.each([409, 503])('keeps HTTP %s without automatically replaying POST', async status => {
  const fetchMock = jest.fn().mockResolvedValue(new Response(JSON.stringify({ status, code: 'INVALID_RESOURCE_STATE', message: 'safe',
    path: '/api/v1/admin/dataset-snapshots/' + id + '/validation', requestId: 'req-1', details: [] }),
  { status, headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'req-1' } }));
  const api = createValidationApi(createWdmApiClient({ fetchImpl: fetchMock }));
  await expect(api.start(id)).rejects.toMatchObject({ status });
  expect(fetchMock).toHaveBeenCalledTimes(1);
});
