import { datasetPeriod } from './dataset-period';

it('maps inclusive Vietnam calendar days to exact half-open UTC bounds', () => {
  expect(datasetPeriod('2026-11-01', '2026-11-02')).toEqual({
    fromTime: '2026-10-31T17:00:00.000Z', toTime: '2026-11-02T17:00:00.000Z',
  });
  expect(datasetPeriod('2026-11-01', '2026-11-01')).toEqual({
    fromTime: '2026-10-31T17:00:00.000Z', toTime: '2026-11-01T17:00:00.000Z',
  });
});

it('handles leap days, year boundaries and the upper DB year without local Date getters', () => {
  expect(datasetPeriod('2024-02-29', '2024-02-29').toTime).toBe('2024-02-29T17:00:00.000Z');
  expect(datasetPeriod('2026-12-31', '2026-12-31').toTime).toBe('2026-12-31T17:00:00.000Z');
  expect(datasetPeriod('9999-12-31', '9999-12-31').toTime).toBe('9999-12-31T17:00:00.000Z');
});

it.each([
  ['', '2026-11-01'], ['2026-11-01', ''], ['2026-11-02', '2026-11-01'],
  ['2026-02-29', '2026-03-01'], ['2026-04-31', '2026-05-01'],
  ['2026-1-01', '2026-11-01'], ['2026-01-01T00:00:00Z', '2026-11-01'],
  ['0999-12-31', '2026-11-01'], ['1000-01-01', '1000-01-02'],
  ['2026-11-01', '10000-01-01'],
])('rejects missing, reversed, malformed and out-of-DB-range days: %s / %s', (from, to) => {
  expect(() => datasetPeriod(from, to)).toThrow();
});
