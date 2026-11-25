import { screen, within } from '@testing-library/react';
import { describe, expect, test } from 'vitest';
import { backend } from '../test/fakeBackend';
import { CRON_LOG, INTERVAL_DB_QUERY, ONE_TIME_LOG, WEEKLY_EMAIL } from '../test/fixtures';
import {
  chooseTask,
  chooseTimingKind,
  expectDialogClosed,
  openEditDialog,
  renderPage,
  saveButton,
} from '../test/render';

describe('editing a schedule', () => {
  test('one time: the form opens filled, with the date shown in local time', async () => {
    backend.schedules = [ONE_TIME_LOG];
    const { user } = renderPage();

    const dialog = await openEditDialog(user, /Log Task/);

    expect(within(dialog).getByRole('combobox', { name: /^task/i })).toHaveTextContent('Log Task');
    expect(within(dialog).getByRole('radio', { name: 'One time' })).toBeChecked();
    // 12:00 UTC on 1 June is 08:00 in New York, the time zone the tests run in.
    expect(within(dialog).getByLabelText(/date and time/i)).toHaveValue('2099-06-01T08:00');
    expect(within(dialog).getByRole('textbox', { name: /message/i })).toHaveValue('hello');
  });

  test('every X: the form opens filled', async () => {
    backend.schedules = [INTERVAL_DB_QUERY];
    const { user } = renderPage();

    const dialog = await openEditDialog(user, /DB Query Task/);

    expect(within(dialog).getByRole('radio', { name: 'Every X' })).toBeChecked();
    expect(within(dialog).getByRole('spinbutton', { name: /every/i })).toHaveValue(15);
    expect(within(dialog).getByRole('combobox', { name: /^unit/i })).toHaveTextContent('Hours');
    expect(within(dialog).getByRole('spinbutton', { name: /limit/i })).toHaveValue(5);
  });

  test('weekly: the form opens filled, with the right days ticked', async () => {
    backend.schedules = [WEEKLY_EMAIL];
    const { user } = renderPage();

    const dialog = await openEditDialog(user, /Dummy Email Task/);

    expect(within(dialog).getByRole('radio', { name: 'Weekly' })).toBeChecked();
    const ticked = within(dialog)
      .getAllByRole('checkbox')
      .filter((checkbox) => (checkbox as HTMLInputElement).checked)
      .map((checkbox) => checkbox.getAttribute('aria-label'));
    expect(ticked).toEqual(['Monday', 'Friday']);
    expect(within(dialog).getAllByRole('checkbox')).toHaveLength(7);
    expect(within(dialog).getByLabelText(/^time/i)).toHaveValue('18:45');
    expect(within(dialog).getByRole('textbox', { name: /^to/i })).toHaveValue('team@example.com');
    expect(within(dialog).getByRole('textbox', { name: /^subject/i })).toHaveValue('Weekly report');
    expect(within(dialog).getByRole('textbox', { name: /^body/i })).toHaveValue('See attached');
  });

  test('cron: the form opens filled', async () => {
    backend.schedules = [CRON_LOG];
    const { user } = renderPage();

    const dialog = await openEditDialog(user, /Log Task/);

    expect(within(dialog).getByRole('radio', { name: 'Cron' })).toBeChecked();
    expect(within(dialog).getByRole('textbox', { name: /cron expression/i })).toHaveValue(
      '0 0/5 * * * ?',
    );
    expect(within(dialog).getByRole('textbox', { name: /message/i })).toHaveValue('tick');
  });

  test('opens the form for the row that was clicked', async () => {
    backend.schedules = [ONE_TIME_LOG, WEEKLY_EMAIL];
    const { user } = renderPage();

    const dialog = await openEditDialog(user, /Dummy Email Task/);

    expect(within(dialog).getByRole('combobox', { name: /^task/i })).toHaveTextContent(
      'Dummy Email Task',
    );
  });

  test('save sends an update for the right id, and the table shows the new values', async () => {
    backend.schedules = [ONE_TIME_LOG, WEEKLY_EMAIL];
    const { user } = renderPage();
    const dialog = await openEditDialog(user, /Log Task/);

    const message = within(dialog).getByRole('textbox', { name: /message/i });
    await user.clear(message);
    await user.type(message, 'updated');
    await chooseTimingKind(user, dialog, 'Every X');
    await user.type(within(dialog).getByRole('spinbutton', { name: /every/i }), '30');
    await user.click(saveButton(dialog));

    await expectDialogClosed();
    expect(backend.writes()).toEqual([
      {
        method: 'PUT',
        path: '/api/schedules/sched-one-time',
        body: {
          taskKey: 'log',
          timing: { type: 'INTERVAL', every: 30, unit: 'MINUTES' },
          timeZone: 'America/New_York',
          parameters: { message: 'updated' },
        },
      },
    ]);
    expect(await screen.findByText('Schedule saved')).toBeInTheDocument();

    const row = await screen.findByRole('row', { name: /Every 30 minutes/ });
    expect(row).toHaveTextContent('Message: updated');
    expect(screen.queryByText(/^Once on /)).not.toBeInTheDocument();
    // The other schedule is untouched.
    expect(screen.getByRole('row', { name: /Mon, Fri at 18:45/ })).toBeInTheDocument();
  });

  test('changing the task swaps the inputs and sends only the new ones', async () => {
    backend.schedules = [CRON_LOG];
    const { user } = renderPage();
    const dialog = await openEditDialog(user, /Log Task/);

    await chooseTask(user, dialog, 'DB Query Task');
    expect(within(dialog).queryByRole('textbox', { name: /message/i })).not.toBeInTheDocument();
    await user.type(within(dialog).getByRole('spinbutton', { name: /limit/i }), '20');
    await user.click(saveButton(dialog));

    await expectDialogClosed();
    const [write] = backend.writes();
    expect(write.method).toBe('PUT');
    expect(write.path).toBe('/api/schedules/sched-cron');
    expect(write.body).toEqual({
      taskKey: 'db-query',
      timing: { type: 'CRON', expression: '0 0/5 * * * ?' },
      timeZone: 'America/New_York',
      parameters: { limit: 20 },
    });
  });

  test('saving without changes sends the same schedule back', async () => {
    backend.schedules = [WEEKLY_EMAIL];
    const { user } = renderPage();
    const dialog = await openEditDialog(user, /Dummy Email Task/);

    await user.click(saveButton(dialog));

    await expectDialogClosed();
    const [write] = backend.writes();
    expect(write.body).toEqual({
      taskKey: WEEKLY_EMAIL.taskKey,
      timing: WEEKLY_EMAIL.timing,
      timeZone: 'America/New_York',
      parameters: WEEKLY_EMAIL.parameters,
    });
  });

  test('clearing an optional input removes it from the schedule', async () => {
    backend.schedules = [WEEKLY_EMAIL];
    const { user } = renderPage();
    const dialog = await openEditDialog(user, /Dummy Email Task/);

    await user.clear(within(dialog).getByRole('textbox', { name: /^body/i }));
    await user.click(saveButton(dialog));

    await expectDialogClosed();
    const [write] = backend.writes();
    expect((write.body as { parameters: unknown }).parameters).toEqual({
      to: 'team@example.com',
      subject: 'Weekly report',
    });
  });
});
