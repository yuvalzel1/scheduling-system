import type { FieldError, ProblemDetail, Schedule, ScheduleRequest, TaskDefinition } from './types';

const BASE_PATH = '/api';

/** Thrown for every response that is not 2xx. Carries the backend's problem details when present. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | undefined;

  constructor(status: number, problem?: ProblemDetail) {
    super(problem?.detail ?? problem?.title ?? `Request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }

  get fieldErrors(): FieldError[] {
    return this.problem?.errors ?? [];
  }
}

async function readProblem(response: Response): Promise<ProblemDetail | undefined> {
  try {
    return (await response.json()) as ProblemDetail;
  } catch {
    return undefined;
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (init.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  const response = await fetch(`${BASE_PATH}${path}`, { ...init, headers });
  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export function listTasks(): Promise<TaskDefinition[]> {
  return request<TaskDefinition[]>('/tasks');
}

export function listSchedules(): Promise<Schedule[]> {
  return request<Schedule[]>('/schedules');
}

export function createSchedule(body: ScheduleRequest): Promise<Schedule> {
  return request<Schedule>('/schedules', { method: 'POST', body: JSON.stringify(body) });
}

export function updateSchedule(id: string, body: ScheduleRequest): Promise<Schedule> {
  return request<Schedule>(`/schedules/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
}

export function deleteSchedule(id: string): Promise<void> {
  return request<void>(`/schedules/${encodeURIComponent(id)}`, { method: 'DELETE' });
}
