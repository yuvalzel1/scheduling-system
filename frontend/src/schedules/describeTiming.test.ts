import { describe, expect, test } from 'vitest';
import { describeTiming, toHoursAndMinutes } from './describeTiming';

describe('describeTiming', () => {
  test('one time: shows the date and time in the given time zone', () => {
    const text = describeTiming(
      { type: 'ONE_TIME', runAt: '2026-12-01T07:00:00Z' },
      { locale: 'en-GB', timeZone: 'UTC' },
    );
    expect(text).toBe('Once on 1 Dec 2026, 07:00');
  });

  test('one time: uses the browser time zone by default', () => {
    // Tests run in America/New_York, which is UTC-5 in December.
    const text = describeTiming(
      { type: 'ONE_TIME', runAt: '2026-12-01T07:00:00Z' },
      { locale: 'en-GB' },
    );
    expect(text).toBe('Once on 1 Dec 2026, 02:00');
  });

  test('one time: falls back to the raw text when the date cannot be read', () => {
    expect(describeTiming({ type: 'ONE_TIME', runAt: 'not-a-date' })).toBe('Once at not-a-date');
  });

  test.each([
    [15, 'MINUTES', 'Every 15 minutes'],
    [1, 'MINUTES', 'Every minute'],
    [2, 'HOURS', 'Every 2 hours'],
    [1, 'HOURS', 'Every hour'],
  ] as const)('every X: %i %s reads "%s"', (every, unit, expected) => {
    expect(describeTiming({ type: 'INTERVAL', every, unit })).toBe(expected);
  });

  test('weekly: lists the days Monday-first, whatever order they arrive in', () => {
    const text = describeTiming({ type: 'WEEKLY', days: ['WEDNESDAY', 'MONDAY'], time: '08:30' });
    expect(text).toBe('Mon, Wed at 08:30');
  });

  test('weekly: all seven days reads "Every day"', () => {
    const text = describeTiming({
      type: 'WEEKLY',
      days: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'],
      time: '00:00',
    });
    expect(text).toBe('Every day at 00:00');
  });

  test('weekly: drops seconds if the server sends them', () => {
    expect(describeTiming({ type: 'WEEKLY', days: ['SUNDAY'], time: '18:45:00' })).toBe(
      'Sun at 18:45',
    );
  });

  test('cron: shows the expression as typed', () => {
    expect(describeTiming({ type: 'CRON', expression: '0 0/5 * * * ?' })).toBe(
      'Cron: 0 0/5 * * * ?',
    );
  });
});

describe('toHoursAndMinutes', () => {
  test('keeps HH:mm', () => {
    expect(toHoursAndMinutes('08:30')).toBe('08:30');
    expect(toHoursAndMinutes('08:30:15')).toBe('08:30');
  });
});
