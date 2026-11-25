import { http, HttpResponse } from 'msw';
import { describe, expect, test } from 'vitest';
import { backend, server } from '../test/fakeBackend';
import { CRON_LOG, TASKS } from '../test/fixtures';
import {
  ApiError,
  createSchedule,
  deleteSchedule,
  listSchedules,
  listTasks,
  updateSchedule,
} from './client';

const BODY = {
  taskKey: 'log',
  timing: { type: 'CRON', expression: '0 0 12 * * ?' },
  timeZone: 'UTC',
  parameters: { message: 'hi' },
} as const;

describe('api client', () => {
  test('reads tasks and schedules', async () => {
    backend.schedules = [CRON_LOG];

    await expect(listTasks()).resolves.toEqual(TASKS);
    await expect(listSchedules()).resolves.toEqual([CRON_LOG]);
  });

  test('create sends JSON and returns the created schedule', async () => {
    let contentType: string | null = null;
    server.events.on('request:start', ({ request }) => {
      contentType = request.headers.get('Content-Type');
    });

    const created = await createSchedule(BODY);

    expect(created).toMatchObject({ id: 'new-1', ...BODY });
    expect(contentType).toBe('application/json');
    expect(backend.writes()[0]).toMatchObject({ method: 'POST', path: '/api/schedules' });
  });

  test('update and delete address the schedule by id', async () => {
    backend.schedules = [CRON_LOG];

    const updated = await updateSchedule(CRON_LOG.id, BODY);
    expect(updated.parameters).toEqual({ message: 'hi' });

    await expect(deleteSchedule(CRON_LOG.id)).resolves.toBeUndefined();
    expect(backend.writes().map((request) => `${request.method} ${request.path}`)).toEqual([
      'PUT /api/schedules/sched-cron',
      'DELETE /api/schedules/sched-cron',
    ]);
  });

  test('a validation failure becomes an ApiError that carries the field errors', async () => {
    server.use(
      http.post('/api/schedules', () =>
        HttpResponse.json(
          {
            title: 'Validation failed',
            status: 400,
            detail: 'The schedule is not valid',
            errors: [{ field: 'parameters.message', message: 'Message is required' }],
          },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    const error = await createSchedule(BODY).catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.message).toBe('The schedule is not valid');
    expect(apiError.fieldErrors).toEqual([
      { field: 'parameters.message', message: 'Message is required' },
    ]);
  });

  test('a not-found answer becomes an ApiError without field errors', async () => {
    const error = await deleteSchedule('missing').catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(404);
    expect((error as ApiError).message).toBe('Not Found');
    expect((error as ApiError).fieldErrors).toEqual([]);
  });

  test('an error answer that is not JSON still becomes an ApiError', async () => {
    server.use(
      http.get(
        '/api/schedules',
        () => new HttpResponse('<html>Bad gateway</html>', { status: 502 }),
      ),
    );

    const error = await listSchedules().catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(502);
    expect((error as ApiError).problem).toBeUndefined();
    expect((error as ApiError).message).toBe('Request failed with status 502');
  });
});
