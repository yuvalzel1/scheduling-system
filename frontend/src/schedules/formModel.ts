import {
  DAYS_OF_WEEK,
  type DayOfWeek,
  type IntervalUnit,
  type ParameterValue,
  type Schedule,
  type ScheduleRequest,
  type TaskDefinition,
  type Timing,
  type TimingType,
} from '../api/types';
import { toHoursAndMinutes } from './describeTiming';

/**
 * What the form holds while the user types. Everything is text, as HTML inputs give it.
 * Only the fields of the selected timing kind and the selected task are mounted, so the
 * others may be missing when the form is submitted.
 */
export interface ScheduleFormValues {
  taskKey: string;
  timingType: TimingType;
  /** One time: local date and time, as a datetime-local input gives it (YYYY-MM-DDTHH:mm). */
  runAt: string;
  /** Every X: the number, as typed. */
  every: string;
  unit: IntervalUnit;
  /** Weekly. */
  days: DayOfWeek[];
  time: string;
  /** Cron. */
  expression: string;
  parameters: Record<string, string>;
}

export type ScheduleFormField =
  keyof Omit<ScheduleFormValues, 'parameters'> | `parameters.${string}`;

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

/** ISO instant → the local wall-clock text a datetime-local input expects. */
export function toLocalDateTimeInput(isoInstant: string): string {
  const date = new Date(isoInstant);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

/** Local wall-clock text from a datetime-local input → ISO instant (UTC). */
export function fromLocalDateTimeInput(localDateTime: string): string {
  return new Date(localDateTime).toISOString();
}

export function browserTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

export function toFormValues(schedule?: Schedule): ScheduleFormValues {
  const values: ScheduleFormValues = {
    taskKey: schedule?.taskKey ?? '',
    timingType: schedule?.timing.type ?? 'ONE_TIME',
    runAt: '',
    every: '',
    unit: 'MINUTES',
    days: [],
    time: '',
    expression: '',
    parameters: {},
  };
  if (!schedule) {
    return values;
  }

  const { timing } = schedule;
  switch (timing.type) {
    case 'ONE_TIME':
      values.runAt = toLocalDateTimeInput(timing.runAt);
      break;
    case 'INTERVAL':
      values.every = String(timing.every);
      values.unit = timing.unit;
      break;
    case 'WEEKLY':
      values.days = DAYS_OF_WEEK.filter((day) => timing.days.includes(day));
      values.time = toHoursAndMinutes(timing.time);
      break;
    case 'CRON':
      values.expression = timing.expression;
      break;
  }

  for (const [name, value] of Object.entries(schedule.parameters)) {
    values.parameters[name] = String(value);
  }
  return values;
}

function toTiming(values: ScheduleFormValues): Timing {
  switch (values.timingType) {
    case 'ONE_TIME':
      return { type: 'ONE_TIME', runAt: fromLocalDateTimeInput(values.runAt) };
    case 'INTERVAL':
      return { type: 'INTERVAL', every: Number(values.every), unit: values.unit };
    case 'WEEKLY':
      return {
        type: 'WEEKLY',
        days: DAYS_OF_WEEK.filter((day) => values.days.includes(day)),
        time: values.time,
      };
    case 'CRON':
      return { type: 'CRON', expression: values.expression.trim() };
  }
}

/**
 * Builds the request body. Only the inputs the selected task declares are sent: numbers as
 * JSON numbers, and optional inputs left empty are left out.
 */
export function toRequest(
  values: ScheduleFormValues,
  task: TaskDefinition,
  timeZone: string = browserTimeZone(),
): ScheduleRequest {
  const parameters: Record<string, ParameterValue> = {};
  for (const parameter of task.parameters) {
    const raw = values.parameters?.[parameter.name] ?? '';
    if (raw.trim() === '') {
      continue;
    }
    parameters[parameter.name] = parameter.type === 'NUMBER' ? Number(raw) : raw;
  }

  return { taskKey: task.key, timing: toTiming(values), timeZone, parameters };
}

const TIMING_FIELDS: Record<string, ScheduleFormField> = {
  timing: 'timingType',
  'timing.type': 'timingType',
  'timing.runAt': 'runAt',
  'timing.every': 'every',
  'timing.unit': 'unit',
  'timing.days': 'days',
  'timing.time': 'time',
  'timing.expression': 'expression',
};

const FIELDS_BY_TIMING_TYPE: Record<TimingType, ScheduleFormField[]> = {
  ONE_TIME: ['runAt'],
  INTERVAL: ['every', 'unit'],
  WEEKLY: ['days', 'time'],
  CRON: ['expression'],
};

/**
 * Maps a field name from a backend validation error onto the form field that is currently on
 * screen. Returns undefined when there is no such field, so the caller can show the message
 * as a general error instead of losing it.
 */
export function toFormField(
  serverField: string,
  timingType: TimingType,
  task: TaskDefinition | undefined,
): ScheduleFormField | undefined {
  if (serverField === 'taskKey') {
    return 'taskKey';
  }
  if (serverField.startsWith('parameters.')) {
    const name = serverField.slice('parameters.'.length);
    return task?.parameters.some((parameter) => parameter.name === name)
      ? `parameters.${name}`
      : undefined;
  }
  const field = TIMING_FIELDS[serverField];
  if (field === undefined) {
    return undefined;
  }
  if (field === 'timingType' || FIELDS_BY_TIMING_TYPE[timingType].includes(field)) {
    return field;
  }
  return undefined;
}
