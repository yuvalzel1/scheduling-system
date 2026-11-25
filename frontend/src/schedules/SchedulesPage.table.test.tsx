import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, test } from 'vitest';
import { backend, server } from '../test/fakeBackend';
import { CRON_LOG, INTERVAL_DB_QUERY, ONE_TIME_LOG, WEEKLY_EMAIL } from '../test/fixtures';
import { renderPage } from '../test/render';

describe('schedules table', () => {
  test('shows a loading state until the schedules arrive', async () => {
    let release: () => void = () => {};
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.get('/api/schedules', async () => {
        await gate;
        return HttpResponse.json([]);
      }),
    );

    renderPage();

    expect(screen.getByRole('progressbar', { name: 'Loading schedules' })).toBeInTheDocument();
    expect(screen.queryByText('No schedules yet')).not.toBeInTheDocument();

    release();
    expect(await screen.findByText('No schedules yet')).toBeInTheDocument();
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  });

  test('shows an empty state when there are no schedules', async () => {
    renderPage();

    expect(await screen.findByText('No schedules yet')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add schedule' })).toBeEnabled();
  });

  test('shows one row per schedule with task, timing in plain words, and inputs', async () => {
    backend.schedules = [ONE_TIME_LOG, INTERVAL_DB_QUERY, WEEKLY_EMAIL, CRON_LOG];

    renderPage();

    const table = await screen.findByRole('table', { name: 'Schedules' });
    const [header, ...rows] = within(table).getAllByRole('row');
    expect(
      within(header)
        .getAllByRole('columnheader')
        .map((cell) => cell.textContent),
    ).toEqual(['Task', 'When', 'Inputs', 'Last run', 'Actions']);
    expect(rows).toHaveLength(4);

    // Task names come from the task list, which loads separately.
    expect(await within(rows[0]).findByText('Log Task')).toBeInTheDocument();
    expect(within(rows[0]).getByText(/^Once on /)).toBeInTheDocument();
    expect(rows[0]).toHaveTextContent('Message: hello');

    expect(within(rows[1]).getByText('DB Query Task')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Every 15 hours')).toBeInTheDocument();
    expect(rows[1]).toHaveTextContent('Limit: 5');

    expect(within(rows[2]).getByText('Dummy Email Task')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Mon, Fri at 18:45')).toBeInTheDocument();
    expect(rows[2]).toHaveTextContent('To: team@example.com');
    expect(rows[2]).toHaveTextContent('Subject: Weekly report');
    expect(rows[2]).toHaveTextContent('Body: See attached');

    expect(within(rows[3]).getByText('Cron: 0 0/5 * * * ?')).toBeInTheDocument();

    for (const row of rows) {
      expect(within(row).getByRole('button', { name: 'Edit schedule' })).toBeInTheDocument();
      expect(within(row).getByRole('button', { name: 'Delete schedule' })).toBeInTheDocument();
    }
  });

  test('says "No inputs" for a schedule without inputs', async () => {
    backend.schedules = [{ ...INTERVAL_DB_QUERY, parameters: {} }];

    renderPage();

    const row = await screen.findByRole('row', { name: /Every 15 hours/ });
    expect(within(row).getByText('No inputs')).toBeInTheDocument();
  });

  test('still shows the rows, with task keys, when the task list fails to load', async () => {
    backend.schedules = [CRON_LOG];
    server.use(http.get('/api/tasks', () => HttpResponse.json({ status: 500 }, { status: 500 })));

    renderPage();

    const row = await screen.findByRole('row', { name: /Cron: 0 0\/5/ });
    expect(within(row).getByRole('rowheader')).toHaveTextContent('log');
    expect(row).toHaveTextContent('message: tick');
  });

  test('shows an error when loading fails, and loads again on retry', async () => {
    backend.schedules = [CRON_LOG];
    server.use(
      http.get(
        '/api/schedules',
        () => HttpResponse.json({ title: 'Internal Server Error', status: 500 }, { status: 500 }),
        { once: true },
      ),
    );

    const { user } = renderPage();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Could not load the schedules.');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();

    await user.click(within(alert).getByRole('button', { name: 'Retry' }));

    expect(await screen.findByRole('table', { name: 'Schedules' })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
