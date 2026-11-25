import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, test } from 'vitest';
import { backend, server } from '../test/fakeBackend';
import { CRON_LOG, WEEKLY_EMAIL } from '../test/fixtures';
import { expectDialogClosed, renderPage } from '../test/render';
import type { UserEvent } from '@testing-library/user-event';

async function openDeleteDialog(user: UserEvent, rowName: RegExp): Promise<HTMLElement> {
  const row = await screen.findByRole('row', { name: rowName });
  await user.click(within(row).getByRole('button', { name: 'Delete schedule' }));
  return screen.findByRole('dialog', { name: 'Delete this schedule?' });
}

describe('deleting a schedule', () => {
  test('asks first, and says which schedule it is about', async () => {
    backend.schedules = [CRON_LOG, WEEKLY_EMAIL];
    const { user } = renderPage();

    const dialog = await openDeleteDialog(user, /Dummy Email Task/);

    expect(dialog).toHaveAccessibleDescription(/Dummy Email Task, Mon, Fri at 18:45/);
    expect(backend.writes()).toEqual([]);
  });

  test('confirm sends the delete for that schedule, and the row disappears', async () => {
    backend.schedules = [CRON_LOG, WEEKLY_EMAIL];
    const { user } = renderPage();
    const dialog = await openDeleteDialog(user, /Dummy Email Task/);

    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));

    await expectDialogClosed();
    expect(backend.writes()).toEqual([{ method: 'DELETE', path: '/api/schedules/sched-weekly' }]);
    expect(screen.queryByRole('row', { name: /Dummy Email Task/ })).not.toBeInTheDocument();
    expect(screen.getByRole('row', { name: /Log Task/ })).toBeInTheDocument();
    expect(await screen.findByText('Schedule deleted')).toBeInTheDocument();
  });

  test('deleting the last schedule brings back the empty state', async () => {
    backend.schedules = [CRON_LOG];
    const { user } = renderPage();
    const dialog = await openDeleteDialog(user, /Log Task/);

    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));

    expect(await screen.findByText('No schedules yet')).toBeInTheDocument();
  });

  test('cancel sends nothing, and the row stays', async () => {
    backend.schedules = [CRON_LOG];
    const { user } = renderPage();
    const dialog = await openDeleteDialog(user, /Log Task/);

    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));

    await expectDialogClosed();
    expect(backend.writes()).toEqual([]);
    expect(screen.getByRole('row', { name: /Log Task/ })).toBeInTheDocument();
    expect(screen.queryByText('Schedule deleted')).not.toBeInTheDocument();
  });

  test('when the delete fails, shows an error and the row stays', async () => {
    backend.schedules = [CRON_LOG];
    server.use(
      http.delete('/api/schedules/:id', () =>
        HttpResponse.json({ title: 'Internal Server Error', status: 500 }, { status: 500 }),
      ),
    );
    const { user } = renderPage();
    const dialog = await openDeleteDialog(user, /Log Task/);

    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Could not delete the schedule. Please try again.',
    );
    expect(within(dialog).getByRole('button', { name: 'Delete' })).toBeEnabled();
    expect(screen.queryByText('Schedule deleted')).not.toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    await expectDialogClosed();
    expect(screen.getByRole('row', { name: /Log Task/ })).toBeInTheDocument();
  });

  test('the delete button is disabled while deleting', async () => {
    backend.schedules = [CRON_LOG];
    let release: () => void = () => {};
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.delete('/api/schedules/:id', async () => {
        await gate;
        backend.schedules = [];
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = renderPage();
    const dialog = await openDeleteDialog(user, /Log Task/);

    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));

    expect(await within(dialog).findByRole('button', { name: 'Deleting…' })).toBeDisabled();
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toBeDisabled();

    release();
    await expectDialogClosed();
    expect(backend.writes()).toHaveLength(1);
  });
});
