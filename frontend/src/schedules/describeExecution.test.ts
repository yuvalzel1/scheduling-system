import { describe, expect, test } from 'vitest';
import { formatDuration, formatMoment } from './describeExecution';

describe('formatMoment', () => {
  test('shows the date and the time with seconds, in the given time zone', () => {
    const text = formatMoment('2026-11-25T09:00:05Z', { locale: 'en-US', timeZone: 'UTC' });

    expect(text).toMatch(/^Nov 25, 2026, 9:00:05\sAM$/);
  });

  test('uses local time by default', () => {
    // The tests run in New York, five hours behind UTC in November.
    expect(formatMoment('2026-11-25T09:00:05Z', { locale: 'en-US' })).toMatch(/4:00:05\sAM$/);
  });

  test('gives back the text as it is when it is not a date', () => {
    expect(formatMoment('not a date')).toBe('not a date');
  });
});

describe('formatDuration', () => {
  test.each([
    ['2026-11-25T09:00:00.000Z', '2026-11-25T09:00:00.000Z', 'under 1 ms'],
    ['2026-11-25T09:00:00.000Z', '2026-11-25T09:00:00.040Z', '40 ms'],
    ['2026-11-25T09:00:00.000Z', '2026-11-25T09:00:00.999Z', '999 ms'],
    ['2026-11-25T09:00:00.000Z', '2026-11-25T09:00:01.500Z', '1.5 s'],
    ['2026-11-25T09:00:00.000Z', '2026-11-25T09:00:59.000Z', '59.0 s'],
    ['2026-11-25T09:00:00.000Z', '2026-11-25T09:02:05.000Z', '2 min 5 s'],
    // Just under a boundary: rounding must carry into the next unit, never show "60".
    ['2026-11-25T09:00:00.000Z', '2026-11-25T09:00:59.940Z', '59.9 s'],
    ['2026-11-25T09:00:00.000Z', '2026-11-25T09:00:59.950Z', '1 min 0 s'],
    ['2026-11-25T09:00:00.000Z', '2026-11-25T09:00:59.999Z', '1 min 0 s'],
    ['2026-11-25T09:00:00.000Z', '2026-11-25T09:01:00.000Z', '1 min 0 s'],
    ['2026-11-25T09:00:00.000Z', '2026-11-25T09:01:59.400Z', '1 min 59 s'],
    ['2026-11-25T09:00:00.000Z', '2026-11-25T09:01:59.500Z', '2 min 0 s'],
    ['2026-11-25T09:00:00.000Z', '2026-11-25T10:00:00.000Z', '60 min 0 s'],
  ])('from %s to %s is "%s"', (startedAt, finishedAt, expected) => {
    expect(formatDuration(startedAt, finishedAt)).toBe(expected);
  });

  test('shows a dash when the times make no sense', () => {
    expect(formatDuration('2026-11-25T09:00:01Z', '2026-11-25T09:00:00Z')).toBe('–');
    expect(formatDuration('nonsense', '2026-11-25T09:00:00Z')).toBe('–');
  });
});
