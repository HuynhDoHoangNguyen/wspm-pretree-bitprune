import { unwrapApiResponse } from './response';

const envelope = { code: 'SUCCESS', message: 'Request completed successfully.', data: { id: '9007199254740993' }, requestId: 'server-id' };

test('unwraps data once and preserves exact string values', () => {
  expect(unwrapApiResponse(envelope, 'server-id')).toEqual(envelope.data);
});

test.each([
  null, [], envelope.data, { ...envelope, code: 'FAILED' }, { ...envelope, message: null },
  { ...envelope, requestId: 'different-id' }, { ...envelope, unexpected: true },
  { code: 'SUCCESS', message: envelope.message, requestId: 'server-id' },
])('rejects legacy, malformed or uncorrelated success without exposing payloads', value => {
  expect(() => unwrapApiResponse(value, 'server-id')).toThrow('INVALID_API_RESPONSE');
});

test('requires server correlation in the response header', () => {
  expect(() => unwrapApiResponse(envelope, null)).toThrow('INVALID_API_RESPONSE');
});
