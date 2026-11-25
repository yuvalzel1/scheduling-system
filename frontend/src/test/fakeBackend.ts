import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import type { Schedule, ScheduleRequest } from '../api/types';
import { TASKS } from './fixtures';

export interface RecordedRequest {
  method: string;
  path: string;
  body?: unknown;
}

let nextId = 1;

/** A tiny in-memory stand-in for the real backend, so the screen refreshes like it would for real. */
export const backend = {
  schedules: [] as Schedule[],
  /** Every request the app made, in order. */
  requests: [] as RecordedRequest[],

  /** Requests that change something: create, edit and delete. */
  writes(): RecordedRequest[] {
    return this.requests.filter((request) => request.method !== 'GET');
  },

  reset(): void {
    this.schedules = [];
    this.requests = [];
    nextId = 1;
  },
};

const NOW = '2026-11-25T09:00:00Z';

const NOT_FOUND = { type: 'about:blank', title: 'Not Found', status: 404 };

export const server = setupServer(
  http.get('/api/tasks', () => HttpResponse.json(TASKS)),

  http.get('/api/schedules', () => HttpResponse.json(backend.schedules)),

  http.post('/api/schedules', async ({ request }) => {
    const body = (await request.json()) as ScheduleRequest;
    const created: Schedule = { ...body, id: `new-${nextId++}`, createdAt: NOW, updatedAt: NOW };
    backend.schedules = [...backend.schedules, created];
    return HttpResponse.json(created, { status: 201 });
  }),

  http.put('/api/schedules/:id', async ({ request, params }) => {
    const existing = backend.schedules.find((schedule) => schedule.id === params.id);
    if (!existing) {
      return HttpResponse.json(NOT_FOUND, { status: 404 });
    }
    const body = (await request.json()) as ScheduleRequest;
    const updated: Schedule = { ...existing, ...body, updatedAt: NOW };
    backend.schedules = backend.schedules.map((schedule) =>
      schedule.id === updated.id ? updated : schedule,
    );
    return HttpResponse.json(updated);
  }),

  http.delete('/api/schedules/:id', ({ params }) => {
    if (!backend.schedules.some((schedule) => schedule.id === params.id)) {
      return HttpResponse.json(NOT_FOUND, { status: 404 });
    }
    backend.schedules = backend.schedules.filter((schedule) => schedule.id !== params.id);
    return new HttpResponse(null, { status: 204 });
  }),
);

// Record every request, including ones a test answers with its own handler.
server.events.on('request:start', ({ request }) => {
  const entry: RecordedRequest = { method: request.method, path: new URL(request.url).pathname };
  backend.requests.push(entry);
  if (request.method === 'POST' || request.method === 'PUT') {
    void request
      .clone()
      .json()
      .then((body: unknown) => {
        entry.body = body;
      });
  }
});
