import { runInThisContext } from 'vm';
import { createWdmApiClient } from './client';
import { createMiningApi } from './mining';
import { MiningRequest, MiningPreview, readMiningPreview, readMiningValidation, sameMiningInput } from './mining-response';
Object.assign(globalThis, runInThisContext('({ Headers, Response })'));
const input: MiningRequest = { snapshotId: '11111111-2222-4333-8444-555555555555', algorithm: 'WSPM_PRETREE', minsupMode: 'PERCENT', minsupValue: 14 };
function data(): MiningPreview { return { snapshotId: input.snapshotId, algorithm: input.algorithm, artifactVersion: 'CONVERSION_ARTIFACT_V1',
  sequenceSha256: 'a'.repeat(64), mappingSha256: 'b'.repeat(64), minimumSupport: { mode: 'PERCENT', configuredValue: 14, relativeFraction: 0.14, effectiveThreshold: 0.42000000000000004 },
  statistics: { sequenceCount: '3', singleItemsetSequenceCount: '1', multiItemsetSequenceCount: '2', itemsetCount: '6', distinctItemCount: '3', totalItemOccurrences: '8', minSequenceLength: '2', maxSequenceLength: '3', averageSequenceLength: 2.6666666666666665 }, executionEligibility: 'NOT_EVALUATED' }; }
function validation() { return { ...data(), sequenceCount: '3', configurationStatus: 'SUPPORTED' }; }
function json(value: unknown) { return new Response(JSON.stringify({ code: 'SUCCESS', message: 'Request completed successfully.', data: value, requestId: 'req-1' }), { headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'req-1' } }); }
function api(fetchMock: jest.Mock) { return createMiningApi(createWdmApiClient({ fetchImpl: fetchMock, getAuthAdapter: () => ({ getAccessToken: () => 'token' }) })); }

it.each(['preview', 'validate'] as const)('sends only four fields to the correct %s endpoint, with no retry', async action => {
  const fetchMock = jest.fn().mockResolvedValue(json(action === 'preview' ? data() : validation()));
  await api(fetchMock)[action]({ ...input, privatePath: 'ignored' } as MiningRequest);
  expect(fetchMock).toHaveBeenCalledTimes(1);
  expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/mining-runs/' + (action === 'preview' ? 'preview' : 'configuration-validation'));
  expect(fetchMock.mock.calls[0][1].method).toBe('POST'); expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(input);
});
it('projects public fields and preserves count strings beyond safe integer precision', () => {
  const raw = data(); raw.statistics.totalItemOccurrences = '9007199254740993';
  const decoded = readMiningPreview({ ...raw, privatePath: 'secret' }, input);
  expect(decoded.statistics.totalItemOccurrences).toBe('9007199254740993'); expect(JSON.stringify(decoded)).not.toContain('secret');
  expect(sameMiningInput(decoded, readMiningValidation(validation(), input))).toBe(true);
});
it.each([['snapshotId', 'wrong'], ['algorithm', 'WSPM_BITPRUNE'], ['artifactVersion', 'unknown'], ['sequenceSha256', 'bad'], ['mappingSha256', null], ['executionEligibility', 'SUPPORTED'], ['statistics', null]])('rejects invalid preview %s', (key, value) => {
  expect(() => readMiningPreview({ ...data(), [String(key)]: value }, input)).toThrow();
});
it.each([['mode', 'ABSOLUTE'], ['configuredValue', 15], ['effectiveThreshold', Infinity], ['effectiveThreshold', -1], ['relativeFraction', null], ['relativeFraction', -0.1], ['relativeFraction', 1.1]])('rejects invalid support %s', (key, value) => {
  expect(() => readMiningPreview({ ...data(), minimumSupport: { ...data().minimumSupport, [String(key)]: value } }, input)).toThrow();
});
it('rejects missing/numeric counts, unknown validation status and a bad response ID', async () => {
  expect(() => readMiningPreview({ ...data(), statistics: { ...data().statistics, sequenceCount: 3 } }, input)).toThrow();
  expect(() => readMiningValidation({ ...validation(), sequenceCount: 3 }, input)).toThrow();
  expect(() => readMiningValidation({ ...validation(), configurationStatus: 'UNKNOWN' }, input)).toThrow();
  const fetchMock = jest.fn().mockResolvedValue(json({ ...data(), snapshotId: 'wrong' }));
  await expect(api(fetchMock).preview(input)).rejects.toMatchObject({ code: 'INVALID_MINING_RESPONSE', requestId: 'req-1' });
});
it('preserves null ABSOLUTE fraction and server tiny threshold without computing formulas', () => {
  const request: MiningRequest = { ...input, minsupMode: 'ABSOLUTE', minsupValue: Number.MIN_VALUE };
  const raw = { ...validation(), minimumSupport: { mode: 'ABSOLUTE', configuredValue: Number.MIN_VALUE, relativeFraction: null, effectiveThreshold: Number.MIN_VALUE } };
  expect(readMiningValidation(raw, request).minimumSupport.effectiveThreshold).toBe(Number.MIN_VALUE);
  expect(() => readMiningValidation({ ...raw, minimumSupport: { ...raw.minimumSupport, relativeFraction: 0 } }, request)).toThrow();
});
it.each([400, 401, 403, 404, 409, 422, 503])('retains HTTP %s, details/request ID and never replays POST', async status => {
  const code = status === 422 ? 'UNSUPPORTED_MINING_CONFIGURATION' : 'TEST_FAILURE';
  const fetchMock = jest.fn().mockResolvedValue(new Response(JSON.stringify({ status, code, message: 'safe', path: '/api/v1/admin/mining-runs/configuration-validation', requestId: 'req-1',
    details: [{ field: 'minsupValue', code: 'ZERO_EFFECTIVE_THRESHOLD', message: 'safe' }] }), { status, headers: { 'Content-Type': 'application/json', 'X-Request-Id': 'req-1' } }));
  await expect(api(fetchMock).validate(input)).rejects.toMatchObject({ status, code, requestId: 'req-1' }); expect(fetchMock).toHaveBeenCalledTimes(1);
});
it.each([NaN, Infinity, -1, 101])('rejects invalid percent %s before fetch', async minsupValue => {
  const fetchMock = jest.fn(); expect(() => api(fetchMock).preview({ ...input, minsupValue })).toThrow(); expect(fetchMock).not.toHaveBeenCalled();
});
it('detects changed server binding without recalculating support', () => {
  const preview = data(), checked = readMiningValidation(validation(), input);
  expect(sameMiningInput(preview, { ...checked, sequenceSha256: 'c'.repeat(64) })).toBe(false);
  expect(sameMiningInput(preview, { ...checked, sequenceCount: '4' })).toBe(false);
  expect(sameMiningInput(preview, { ...checked, minimumSupport: { ...checked.minimumSupport, effectiveThreshold: 1 } })).toBe(false);
});
it('rejects trailing line terminators in canonical IDs, hashes and counts', () => {
  const fetchMock = jest.fn(); expect(() => api(fetchMock).preview({ ...input, snapshotId: input.snapshotId + '\n' })).toThrow();
  expect(fetchMock).not.toHaveBeenCalled();
  expect(() => readMiningPreview({ ...data(), sequenceSha256: 'a'.repeat(64) + '\n' }, input)).toThrow();
  expect(() => readMiningValidation({ ...validation(), sequenceCount: '3\n' }, input)).toThrow();
});
