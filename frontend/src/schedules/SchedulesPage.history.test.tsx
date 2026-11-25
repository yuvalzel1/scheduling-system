import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, test } from 'vitest';
import type { Execution, Schedule } from '../api/types';
import { backend, server } from '../test/fakeBackend';
import { CRON_LOG, INTERVAL_DB_QUERY, ONE_TIME_LOG } from '../test/fixtures';
import { renderPage } from '../test/render';

// Times are shown in New York time, the zone the tests run in (UTC-5 in November).
const SUCCEEDED: Execution = {
  id: 'run-1',
  taskKey: 'log',
  status: 'SUCCEEDED',
  startedAt: '2026-11-25T09:00:00.000Z',
  finishedAt: '2026-11-25T09:00:00.040Z',
  errorMessage: null,
};

const FAILED: Execution = {
  id: 'run-2',
  taskKey: 'log',
  status: 'FAILED',
  startedAt: '2026-11-25T09:05:00.000Z',
  finishedAt: '2026-11-25T09:05:01.500Z',
  errorMessage: 'IllegalStateException: the disk is full',
};

function withLastRun(schedule: Schedule, lastExecution: Execution | null): Schedule {
  return { ...schedule, lastExecution };
}

async function openHistory(user: ReturnType<typeof renderPage>['user'], rowName: RegExp) {
  const row = await screen.findByRole('row', { name: rowName });
  await user.click(within(row).getByRole('button', { name: 'Run history' }));
  return screen.findByRole('dialog', { name: 'Run history' });
}

describe('last run in the table', () => {
  test('says so when a schedule has not run yet', async () => {
    backend.schedules = [withLastRun(CRON_LOG, null), INTERVAL_DB_QUERY];

    renderPage();

    const rows = (await screen.findAllByRole('row')).slice(1);
    expect(within(rows[0]).getByText('Not run yet')).toBeInTheDocument();
    // A schedule object without the field at all is treated the same way.
    expect(within(rows[1]).getByText('Not run yet')).toBeInTheDocument();
  });

  test('shows how the newest run ended and when it started', async () => {
    backend.schedules = [withLastRun(CRON_LOG, SUCCEEDED), withLastRun(ONE_TIME_LOG, FAILED)];

    renderPage();

    const rows = (await screen.findAllByRole('row')).slice(1);
    expect(within(rows[0]).getByText('Succeeded')).toBeInTheDocument();
    expect(within(rows[0]).getByText(/Nov 25, 2026, 4:00:00\sAM/)).toBeInTheDocument();
    expect(within(rows[1]).getByText('Failed')).toBeInTheDocument();
    expect(within(rows[1]).getByText(/Nov 25, 2026, 4:05:00\sAM/)).toBeInTheDocument();
  });

  test('picks up a new run by itself, without the user doing anything', async () => {
    backend.schedules = [withLastRun(CRON_LOG, null)];
    renderPage();
    expect(await screen.findByText('Not run yet')).toBeInTheDocument();

    // The task runs on the server while the page is just sitting there.
    backend.schedules = [withLastRun(CRON_LOG, SUCCEEDED)];

    expect(await screen.findByText('Succeeded', {}, { timeout: 9000 })).toBeInTheDocument();
    expect(screen.queryByText('Not run yet')).not.toBeInTheDocument();
  });
});

describe('run history dialog', () => {
  test('lists the runs newest first with time, duration, task, result and error', async () => {
    backend.schedules = [withLastRun(CRON_LOG, FAILED)];
    backend.executions[CRON_LOG.id] = [FAILED, SUCCEEDED];
    const { user } = renderPage();

    const dialog = await openHistory(user, /tick/);

    expect(within(dialog).getByText('Log Task, Cron: 0 0/5 * * * ?')).toBeInTheDocument();
    const table = await within(dialog).findByRole('table', { name: 'Runs' });
    const [header, ...rows] = within(table).getAllByRole('row');
    expect(
      within(header)
        .getAllByRole('columnheader')
        .map((cell) => cell.textContent),
    ).toEqual(['Started', 'Took', 'Task', 'Result', 'Error']);
    expect(rows).toHaveLength(2);

    expect(rows[0]).toHaveTextContent(/Nov 25, 2026, 4:05:00\sAM/);
    expect(rows[0]).toHaveTextContent('1.5 s');
    expect(rows[0]).toHaveTextContent('Log Task');
    expect(rows[0]).toHaveTextContent('Failed');
    expect(rows[0]).toHaveTextContent('IllegalStateException: the disk is full');

    expect(rows[1]).toHaveTextContent(/Nov 25, 2026, 4:00:00\sAM/);
    expect(rows[1]).toHaveTextContent('40 ms');
    expect(rows[1]).toHaveTextContent('Succeeded');
    expect(rows[1]).not.toHaveTextContent('Exception');
  });

  test('asks the backend for the history of the right schedule', async () => {
    backend.schedules = [CRON_LOG, INTERVAL_DB_QUERY];
    const { user } = renderPage();

    await openHistory(user, /DB Query Task/);

    await waitFor(() =>
      expect(backend.requests.map((request) => request.path)).toContain(
        `/api/schedules/${INTERVAL_DB_QUERY.id}/executions`,
      ),
    );
    expect(backend.requests.map((request) => request.path)).not.toContain(
      `/api/schedules/${CRON_LOG.id}/executions`,
    );
  });

  test('names the task that ran, even if the schedule now uses another task', async () => {
    backend.schedules = [CRON_LOG];
    backend.executions[CRON_LOG.id] = [{ ...SUCCEEDED, taskKey: 'email' }];
    const { user } = renderPage();

    const dialog = await openHistory(user, /tick/);

    const table = await within(dialog).findByRole('table', { name: 'Runs' });
    expect(within(table).getByText('Dummy Email Task')).toBeInTheDocument();
  });

  test('says so when the schedule has not run yet', async () => {
    backend.schedules = [CRON_LOG];
    const { user } = renderPage();

    const dialog = await openHistory(user, /tick/);

    expect(await within(dialog).findByText('This schedule has not run yet.')).toBeInTheDocument();
    expect(within(dialog).queryByRole('table')).not.toBeInTheDocument();
  });

  test('shows a loading state until the history arrives', async () => {
    backend.schedules = [CRON_LOG];
    let release: () => void = () => {};
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.get('/api/schedules/:id/executions', async () => {
        await gate;
        return HttpResponse.json([SUCCEEDED]);
      }),
    );
    const { user } = renderPage();

    const dialog = await openHistory(user, /tick/);

    expect(
      within(dialog).getByRole('progressbar', { name: 'Loading run history' }),
    ).toBeInTheDocument();
    release();
    expect(await within(dialog).findByRole('table', { name: 'Runs' })).toBeInTheDocument();
    expect(within(dialog).queryByRole('progressbar')).not.toBeInTheDocument();
  });

  test('shows an error with a retry when the history cannot be loaded', async () => {
    backend.schedules = [CRON_LOG];
    backend.executions[CRON_LOG.id] = [SUCCEEDED];
    server.use(
      http.get(
        '/api/schedules/:id/executions',
        () => HttpResponse.json({ status: 500 }, { status: 500 }),
        {
          once: true,
        },
      ),
    );
    const { user } = renderPage();

    const dialog = await openHistory(user, /tick/);

    expect(await within(dialog).findByText('Could not load the run history.')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Retry' }));
    expect(await within(dialog).findByRole('table', { name: 'Runs' })).toBeInTheDocument();
    expect(within(dialog).queryByText('Could not load the run history.')).not.toBeInTheDocument();
  });

  test('closes with the Close button and leaves the table as it was', async () => {
    backend.schedules = [CRON_LOG];
    const { user } = renderPage();
    const dialog = await openHistory(user, /tick/);

    await user.click(within(dialog).getByRole('button', { name: 'Close' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByRole('table', { name: 'Schedules' })).toBeInTheDocument();
    expect(backend.writes()).toEqual([]);
  });
});
