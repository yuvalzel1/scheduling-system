import { screen, within } from '@testing-library/react';
import { describe, expect, test } from 'vitest';
import type { Schedule } from '../api/types';
import { backend } from '../test/fakeBackend';
import { CRON_LOG, ONE_TIME_LOG, WEEKLY_EMAIL } from '../test/fixtures';
import {
  chooseTask,
  chooseTimingKind,
  expectDialogClosed,
  openAddDialog,
  openEditDialog,
  renderPage,
  saveButton,
} from '../test/render';

// The tests run in New York. These schedules were created by someone in Berlin.
const BERLIN_WEEKLY: Schedule = { ...WEEKLY_EMAIL, timeZone: 'Europe/Berlin' };
const BERLIN_CRON: Schedule = { ...CRON_LOG, timeZone: 'Europe/Berlin' };

describe('time zone of a schedule', () => {
  test('editing keeps the zone the schedule was created in, not the zone of whoever edits it', async () => {
    backend.schedules = [BERLIN_WEEKLY];
    const { user } = renderPage();
    const dialog = await openEditDialog(user, /Weekly report/);

    const subject = within(dialog).getByRole('textbox', { name: /subject/i });
    await user.clear(subject);
    await user.type(subject, 'Only the subject changed');
    await user.click(saveButton(dialog));

    await expectDialogClosed();
    expect(backend.writes()).toEqual([
      {
        method: 'PUT',
        path: `/api/schedules/${BERLIN_WEEKLY.id}`,
        body: {
          taskKey: 'email',
          timing: { type: 'WEEKLY', days: ['MONDAY', 'FRIDAY'], time: '18:45' },
          timeZone: 'Europe/Berlin',
          parameters: {
            to: 'team@example.com',
            subject: 'Only the subject changed',
            body: 'See attached',
          },
        },
      },
    ]);
  });

  test('the edit form says which zone a weekly time is in', async () => {
    backend.schedules = [BERLIN_WEEKLY];
    const { user } = renderPage();

    const dialog = await openEditDialog(user, /Weekly report/);

    expect(within(dialog).getByLabelText(/^time/i)).toHaveAccessibleDescription(
      'Time zone: Europe/Berlin',
    );
  });

  test('the edit form says which zone a cron expression is in', async () => {
    backend.schedules = [BERLIN_CRON];
    const { user } = renderPage();

    const dialog = await openEditDialog(user, /tick/);

    expect(
      within(dialog).getByRole('textbox', { name: /cron expression/i }),
    ).toHaveAccessibleDescription(/Time zone: Europe\/Berlin\.$/);
  });

  test('a new schedule uses, and shows, the zone of the browser', async () => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);
    await chooseTask(user, dialog, 'Log Task');

    await chooseTimingKind(user, dialog, 'Weekly');
    expect(within(dialog).getByLabelText(/^time/i)).toHaveAccessibleDescription(
      'Time zone: America/New_York',
    );

    await chooseTimingKind(user, dialog, 'Cron');
    expect(
      within(dialog).getByRole('textbox', { name: /cron expression/i }),
    ).toHaveAccessibleDescription(/Time zone: America\/New_York\.$/);
  });

  test('the table names the zone of a weekly or cron schedule only when it is not the browser zone', async () => {
    backend.schedules = [BERLIN_WEEKLY, BERLIN_CRON, WEEKLY_EMAIL, CRON_LOG];
    renderPage();

    const rows = (await screen.findAllByRole('row')).slice(1);

    expect(within(rows[0]).getByText('Europe/Berlin time')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Europe/Berlin time')).toBeInTheDocument();
    expect(rows[2]).not.toHaveTextContent(/New_York|Berlin/);
    expect(rows[3]).not.toHaveTextContent(/New_York|Berlin/);
  });

  test('the table does not name a zone for a one-time schedule, which is an exact moment', async () => {
    backend.schedules = [{ ...ONE_TIME_LOG, timeZone: 'Europe/Berlin' }];
    renderPage();

    const rows = (await screen.findAllByRole('row')).slice(1);

    expect(within(rows[0]).getByText(/^Once on /)).toBeInTheDocument();
    expect(rows[0]).not.toHaveTextContent('Berlin');
  });
});
