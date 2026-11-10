import { readComboInput } from './combo-input';
const id = '10000000-0000-4000-8000-000000000001';
const value = () => ({ runId: id, snapshotId: id, publicationDigest: 'a'.repeat(64),
  patternCount: '123', dictionaryCount: '20', inputStatus: 'VERIFIED_PUBLISHED_INPUT',
  generationStatus: 'BLOCKED', blockers: ['BUSINESS_POLICY_UNCONFIRMED'] });
test('keeps exact counts and separates verified input from business permission', () => {
  const v = value(); const parsed = readComboInput({ ...v, internalPath: '/private' }, id);
  expect(parsed.patternCount).toBe('123'); expect(parsed.generationStatus).toBe('BLOCKED');
  expect(parsed).not.toHaveProperty('internalPath');
  v.blockers.push('changed'); expect(parsed.blockers).toHaveLength(1);
});
test.each([{ runId: id.replace(/1$/, '2') }, { patternCount: 123 }, { publicationDigest: 'not-hash' },
  { generationStatus: 'READY' }, { blockers: [] }, { dictionaryCount: '10001' }])('rejects malformed or unbound input %s', patch => {
  expect(() => readComboInput({ ...value(), ...patch }, id)).toThrow();
});
