/** Types that mirror the backend API contract. */

export type ParameterType = 'STRING' | 'NUMBER';

export interface TaskParameter {
  name: string;
  label: string;
  type: ParameterType;
  required: boolean;
  description?: string;
}

export interface TaskDefinition {
  key: string;
  name: string;
  description: string;
  parameters: TaskParameter[];
}

export const DAYS_OF_WEEK = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
] as const;

export type DayOfWeek = (typeof DAYS_OF_WEEK)[number];

export type IntervalUnit = 'MINUTES' | 'HOURS';

export type Timing =
  | { type: 'ONE_TIME'; runAt: string }
  | { type: 'INTERVAL'; every: number; unit: IntervalUnit }
  | { type: 'WEEKLY'; days: DayOfWeek[]; time: string }
  | { type: 'CRON'; expression: string };

export type TimingType = Timing['type'];

export type ParameterValue = string | number;

export interface ScheduleRequest {
  taskKey: string;
  timing: Timing;
  timeZone: string;
  parameters: Record<string, ParameterValue>;
}

export type ExecutionStatus = 'SUCCEEDED' | 'FAILED';

/** One finished run of a schedule's task. */
export interface Execution {
  id: string;
  /** The task that ran. A schedule can later be changed to another task. */
  taskKey: string;
  status: ExecutionStatus;
  startedAt: string;
  finishedAt: string;
  /** Why the run failed; null when it succeeded. */
  errorMessage: string | null;
}

export interface Schedule extends ScheduleRequest {
  id: string;
  createdAt: string;
  updatedAt: string;
  /** The newest run; null or missing when the schedule has not run yet. */
  lastExecution?: Execution | null;
}

export interface FieldError {
  field: string;
  message: string;
}

/** RFC 9457 problem details, plus the per-field list the backend adds on validation errors. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: FieldError[];
}
