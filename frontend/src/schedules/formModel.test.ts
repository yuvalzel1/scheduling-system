import { describe, expect, test } from 'vitest';
import { CRON_LOG, INTERVAL_DB_QUERY, ONE_TIME_LOG, TASKS, WEEKLY_EMAIL } from '../test/fixtures';
import {
  browserTimeZone,
  fromLocalDateTimeInput,
  toFormField,
  toFormValues,
  toLocalDateTimeInput,
  toRequest,
  type ScheduleFormValues,
} from './formModel';

const [LOG, EMAIL, DB_QUERY] = TASKS;

const EMPTY: ScheduleFormValues = {
  taskKey: '',
  timingType: 'ONE_TIME',
  runAt: '',
  every: '',
  unit: 'MINUTES',
  days: [],
  time: '',
  expression: '',
  parameters: {},
};

/** A form that is valid apart from the inputs, for tests that only look at the inputs. */
const WITH_CRON: ScheduleFormValues = { ...EMPTY, timingType: 'CRON', expression: '0 0 12 * * ?' };

// These tests run in America/New_York (set in vite.config.ts).
describe('local date-time conversion', () => {
  test('the browser time zone is the one the tests pin', () => {
    expect(browserTimeZone()).toBe('America/New_York');
  });

  test('local text becomes an exact UTC moment', () => {
    expect(fromLocalDateTimeInput('2099-01-15T09:30')).toBe('2099-01-15T14:30:00.000Z');
  });

  test('a UTC moment becomes local text, with daylight saving applied', () => {
    expect(toLocalDateTimeInput('2099-01-15T14:30:00Z')).toBe('2099-01-15T09:30');
    expect(toLocalDateTimeInput('2099-06-01T12:00:00Z')).toBe('2099-06-01T08:00');
  });

  test('an unreadable moment becomes an empty input', () => {
    expect(toLocalDateTimeInput('nonsense')).toBe('');
  });
});

describe('toFormValues', () => {
  test('a new schedule starts empty, on "one time"', () => {
    expect(toFormValues()).toEqual(EMPTY);
  });

  test('one time', () => {
    expect(toFormValues(ONE_TIME_LOG)).toEqual({
      ...EMPTY,
      taskKey: 'log',
      runAt: '2099-06-01T08:00',
      parameters: { message: 'hello' },
    });
  });

  test('every X: numbers become text for the inputs', () => {
    expect(toFormValues(INTERVAL_DB_QUERY)).toEqual({
      ...EMPTY,
      taskKey: 'db-query',
      timingType: 'INTERVAL',
      every: '15',
      unit: 'HOURS',
      parameters: { limit: '5' },
    });
  });

  test('weekly', () => {
    expect(toFormValues(WEEKLY_EMAIL)).toEqual({
      ...EMPTY,
      taskKey: 'email',
      timingType: 'WEEKLY',
      days: ['MONDAY', 'FRIDAY'],
      time: '18:45',
      parameters: { to: 'team@example.com', subject: 'Weekly report', body: 'See attached' },
    });
  });

  test('cron', () => {
    expect(toFormValues(CRON_LOG)).toEqual({
      ...EMPTY,
      taskKey: 'log',
      timingType: 'CRON',
      expression: '0 0/5 * * * ?',
      parameters: { message: 'tick' },
    });
  });
});

describe('toRequest', () => {
  test('sends only the fields of the selected timing kind', () => {
    const request = toRequest(
      {
        ...EMPTY,
        timingType: 'CRON',
        expression: '  0 0 12 * * ?  ',
        every: '15',
        runAt: '2099-01-15T09:30',
        days: ['MONDAY'],
        time: '08:30',
        parameters: { message: 'hi' },
      },
      LOG,
      'Europe/Paris',
    );
    expect(request).toEqual({
      taskKey: 'log',
      timing: { type: 'CRON', expression: '0 0 12 * * ?' },
      timeZone: 'Europe/Paris',
      parameters: { message: 'hi' },
    });
  });

  test('uses the browser time zone when none is given', () => {
    const request = toRequest({ ...EMPTY, runAt: '2099-01-15T09:30' }, DB_QUERY);
    expect(request.timeZone).toBe('America/New_York');
    expect(request.timing).toEqual({ type: 'ONE_TIME', runAt: '2099-01-15T14:30:00.000Z' });
  });

  test('every X is sent as a number', () => {
    const request = toRequest(
      { ...EMPTY, timingType: 'INTERVAL', every: '15', unit: 'HOURS' },
      DB_QUERY,
    );
    expect(request.timing).toEqual({ type: 'INTERVAL', every: 15, unit: 'HOURS' });
  });

  test('weekly days are sent Monday-first', () => {
    const request = toRequest(
      { ...EMPTY, timingType: 'WEEKLY', days: ['FRIDAY', 'MONDAY'], time: '08:30' },
      DB_QUERY,
    );
    expect(request.timing).toEqual({ type: 'WEEKLY', days: ['MONDAY', 'FRIDAY'], time: '08:30' });
  });

  test('number inputs are sent as numbers, text inputs as text', () => {
    const numbers = toRequest({ ...WITH_CRON, parameters: { limit: '5' } }, DB_QUERY);
    expect(numbers.parameters).toEqual({ limit: 5 });

    const text = toRequest({ ...WITH_CRON, parameters: { message: '5' } }, LOG);
    expect(text.parameters).toEqual({ message: '5' });
  });

  test('optional inputs left empty are left out', () => {
    const request = toRequest(
      { ...WITH_CRON, parameters: { to: 'a@example.com', subject: 'Hi', body: '   ' } },
      EMAIL,
    );
    expect(request.parameters).toEqual({ to: 'a@example.com', subject: 'Hi' });
  });

  test('inputs the selected task does not declare are not sent', () => {
    const request = toRequest(
      { ...WITH_CRON, parameters: { message: 'left over', to: 'a@example.com', subject: 'Hi' } },
      EMAIL,
    );
    expect(request.parameters).toEqual({ to: 'a@example.com', subject: 'Hi' });
  });

  test('works when the form holds no inputs at all', () => {
    const values = { ...WITH_CRON } as Partial<ScheduleFormValues>;
    delete values.parameters;
    const request = toRequest(values as ScheduleFormValues, DB_QUERY);
    expect(request.parameters).toEqual({});
  });
});

describe('toFormField', () => {
  test('maps the task field', () => {
    expect(toFormField('taskKey', 'CRON', LOG)).toBe('taskKey');
  });

  test.each([
    ['timing.runAt', 'ONE_TIME', 'runAt'],
    ['timing.every', 'INTERVAL', 'every'],
    ['timing.unit', 'INTERVAL', 'unit'],
    ['timing.days', 'WEEKLY', 'days'],
    ['timing.time', 'WEEKLY', 'time'],
    ['timing.expression', 'CRON', 'expression'],
    ['timing', 'CRON', 'timingType'],
    ['timing.type', 'WEEKLY', 'timingType'],
  ] as const)('maps %s while %s is selected', (serverField, timingType, expected) => {
    expect(toFormField(serverField, timingType, LOG)).toBe(expected);
  });

  test('a timing field of another kind is not on screen, so it is not mapped', () => {
    expect(toFormField('timing.every', 'CRON', LOG)).toBeUndefined();
  });

  test('maps an input of the selected task', () => {
    expect(toFormField('parameters.message', 'CRON', LOG)).toBe('parameters.message');
  });

  test('does not map an input the selected task does not have', () => {
    expect(toFormField('parameters.message', 'CRON', EMAIL)).toBeUndefined();
    expect(toFormField('parameters.message', 'CRON', undefined)).toBeUndefined();
  });

  test('does not map fields the form does not show', () => {
    expect(toFormField('timeZone', 'CRON', LOG)).toBeUndefined();
    expect(toFormField('something.else', 'CRON', LOG)).toBeUndefined();
  });
});
