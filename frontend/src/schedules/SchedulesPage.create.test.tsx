import { fireEvent, screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, test } from 'vitest';
import { backend, server } from '../test/fakeBackend';
import {
  chooseOption,
  chooseTask,
  chooseTimingKind,
  expectDialogClosed,
  openAddDialog,
  renderPage,
  saveButton,
  setInputValue,
} from '../test/render';

// Tests run in America/New_York (set in vite.config.ts), so 09:30 local in January is 14:30 UTC.
const LOCAL_FUTURE = '2099-01-15T09:30';
const UTC_FUTURE = '2099-01-15T14:30:00.000Z';
const TIME_ZONE = 'America/New_York';

describe('creating a schedule', () => {
  test('one time: sends the picked local time as an exact moment, with the browser time zone', async () => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);

    await chooseTask(user, dialog, 'Log Task');
    setInputValue(within(dialog).getByLabelText(/date and time/i), LOCAL_FUTURE);
    await user.type(within(dialog).getByRole('textbox', { name: /message/i }), 'hello');
    await user.click(saveButton(dialog));

    await expectDialogClosed();
    expect(backend.writes()).toEqual([
      {
        method: 'POST',
        path: '/api/schedules',
        body: {
          taskKey: 'log',
          timing: { type: 'ONE_TIME', runAt: UTC_FUTURE },
          timeZone: TIME_ZONE,
          parameters: { message: 'hello' },
        },
      },
    ]);
    expect(await screen.findByText('Schedule saved')).toBeInTheDocument();
    const row = await screen.findByRole('row', { name: /Log Task/ });
    expect(row).toHaveTextContent('Message: hello');
  });

  test('every X: sends the number and the unit', async () => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);

    await chooseTask(user, dialog, 'Dummy Email Task');
    await chooseTimingKind(user, dialog, 'Every X');
    await user.type(within(dialog).getByRole('spinbutton', { name: /every/i }), '15');
    await chooseOption(user, dialog, /^unit/i, 'Hours');
    await user.type(within(dialog).getByRole('textbox', { name: /^to/i }), 'team@example.com');
    await user.type(within(dialog).getByRole('textbox', { name: /^subject/i }), 'Report');
    await user.click(saveButton(dialog));

    await expectDialogClosed();
    expect(backend.writes()).toEqual([
      {
        method: 'POST',
        path: '/api/schedules',
        body: {
          taskKey: 'email',
          timing: { type: 'INTERVAL', every: 15, unit: 'HOURS' },
          timeZone: TIME_ZONE,
          // "Body" is optional and was left empty, so it is not sent at all.
          parameters: { to: 'team@example.com', subject: 'Report' },
        },
      },
    ]);
    expect(await screen.findByRole('row', { name: /Every 15 hours/ })).toBeInTheDocument();
  });

  test('weekly: sends the picked days Monday-first and the time; a number input is sent as a number', async () => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);

    await chooseTask(user, dialog, 'DB Query Task');
    await chooseTimingKind(user, dialog, 'Weekly');
    await user.click(within(dialog).getByRole('checkbox', { name: 'Wednesday' }));
    await user.click(within(dialog).getByRole('checkbox', { name: 'Monday' }));
    setInputValue(within(dialog).getByLabelText(/^time/i), '08:30');
    await user.type(within(dialog).getByRole('spinbutton', { name: /limit/i }), '5');
    await user.click(saveButton(dialog));

    await expectDialogClosed();
    const [write] = backend.writes();
    expect(write.body).toEqual({
      taskKey: 'db-query',
      timing: { type: 'WEEKLY', days: ['MONDAY', 'WEDNESDAY'], time: '08:30' },
      timeZone: TIME_ZONE,
      parameters: { limit: 5 },
    });
    expect((write.body as { parameters: { limit: unknown } }).parameters.limit).toBeTypeOf(
      'number',
    );
    expect(await screen.findByRole('row', { name: /Mon, Wed at 08:30/ })).toBeInTheDocument();
  });

  test('cron: sends the expression as typed; an optional input left empty is not sent', async () => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);

    await chooseTask(user, dialog, 'DB Query Task');
    await chooseTimingKind(user, dialog, 'Cron');
    await user.type(
      within(dialog).getByRole('textbox', { name: /cron expression/i }),
      '0 0/5 * * * ?',
    );
    await user.click(saveButton(dialog));

    await expectDialogClosed();
    expect(backend.writes()).toEqual([
      {
        method: 'POST',
        path: '/api/schedules',
        body: {
          taskKey: 'db-query',
          timing: { type: 'CRON', expression: '0 0/5 * * * ?' },
          timeZone: TIME_ZONE,
          parameters: {},
        },
      },
    ]);
    expect(
      await screen.findByRole('row', { name: /Cron: 0 0\/5 \* \* \* \?/ }),
    ).toBeInTheDocument();
  });

  test('cron: explains the Quartz format', async () => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);

    await chooseTimingKind(user, dialog, 'Cron');

    const input = within(dialog).getByRole('textbox', { name: /cron expression/i });
    expect(input).toHaveAccessibleDescription(/6 or 7 parts, starting with seconds/);
    expect(input).toHaveAccessibleDescription(/0 0\/5 \* \* \* \?/);
  });
});

describe('the form blocks saving', () => {
  test('when no task is picked', async () => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);

    setInputValue(within(dialog).getByLabelText(/date and time/i), LOCAL_FUTURE);
    await user.click(saveButton(dialog));

    expect(await within(dialog).findByText('Pick a task')).toBeInTheDocument();
    expect(backend.writes()).toEqual([]);
  });

  test('when a required input is missing, and shows the error on that field', async () => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);

    await chooseTask(user, dialog, 'Log Task');
    setInputValue(within(dialog).getByLabelText(/date and time/i), LOCAL_FUTURE);
    const message = within(dialog).getByRole('textbox', { name: /message/i });
    expect(message).toBeRequired();
    await user.click(saveButton(dialog));

    expect(await within(dialog).findByText('Message is required')).toBeInTheDocument();
    expect(message).toBeInvalid();
    expect(message).toHaveAccessibleDescription('Message is required');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(backend.writes()).toEqual([]);
  });

  test('when a required input holds only spaces', async () => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);

    await chooseTask(user, dialog, 'Log Task');
    setInputValue(within(dialog).getByLabelText(/date and time/i), LOCAL_FUTURE);
    await user.type(within(dialog).getByRole('textbox', { name: /message/i }), '   ');
    await user.click(saveButton(dialog));

    expect(await within(dialog).findByText('Message is required')).toBeInTheDocument();
    expect(backend.writes()).toEqual([]);
  });

  test('marks optional inputs as not required', async () => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);

    await chooseTask(user, dialog, 'Dummy Email Task');

    expect(within(dialog).getByRole('textbox', { name: /^to/i })).toBeRequired();
    expect(within(dialog).getByRole('textbox', { name: /^subject/i })).toBeRequired();
    expect(within(dialog).getByRole('textbox', { name: /^body/i })).not.toBeRequired();
  });

  test('weekly with no day picked', async () => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);

    await chooseTask(user, dialog, 'DB Query Task');
    await chooseTimingKind(user, dialog, 'Weekly');
    setInputValue(within(dialog).getByLabelText(/^time/i), '08:30');
    await user.click(saveButton(dialog));

    expect(await within(dialog).findByText('Pick at least one day')).toBeInTheDocument();
    expect(backend.writes()).toEqual([]);
  });

  test('weekly with no time', async () => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);

    await chooseTask(user, dialog, 'DB Query Task');
    await chooseTimingKind(user, dialog, 'Weekly');
    await user.click(within(dialog).getByRole('checkbox', { name: 'Monday' }));
    await user.click(saveButton(dialog));

    expect(await within(dialog).findByText('Pick a time')).toBeInTheDocument();
    expect(backend.writes()).toEqual([]);
  });

  test.each([
    ['zero', '0'],
    ['a negative number', '-5'],
    ['letters', 'abc'],
    ['a fraction', '1.5'],
    ['nothing', ''],
  ])('every X with %s', async (_label, typed) => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);

    await chooseTask(user, dialog, 'DB Query Task');
    await chooseTimingKind(user, dialog, 'Every X');
    if (typed !== '') {
      await user.type(within(dialog).getByRole('spinbutton', { name: /every/i }), typed);
    }
    await user.click(saveButton(dialog));

    expect(await within(dialog).findByText('Enter a whole number, 1 or more')).toBeInTheDocument();
    expect(backend.writes()).toEqual([]);
  });

  test('one time with a date in the past', async () => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);

    await chooseTask(user, dialog, 'DB Query Task');
    setInputValue(within(dialog).getByLabelText(/date and time/i), '2000-01-01T10:00');
    await user.click(saveButton(dialog));

    expect(
      await within(dialog).findByText('Pick a date and time in the future'),
    ).toBeInTheDocument();
    expect(backend.writes()).toEqual([]);
  });

  test('one time with no date', async () => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);

    await chooseTask(user, dialog, 'DB Query Task');
    await user.click(saveButton(dialog));

    expect(await within(dialog).findByText('Pick a date and time')).toBeInTheDocument();
    expect(backend.writes()).toEqual([]);
  });

  test('cron left empty', async () => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);

    await chooseTask(user, dialog, 'DB Query Task');
    await chooseTimingKind(user, dialog, 'Cron');
    await user.click(saveButton(dialog));

    expect(await within(dialog).findByText(/Enter a cron expression/)).toBeInTheDocument();
    expect(backend.writes()).toEqual([]);
  });

  test('lets the user fix the problem and save', async () => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);

    await chooseTask(user, dialog, 'Log Task');
    await chooseTimingKind(user, dialog, 'Cron');
    await user.type(
      within(dialog).getByRole('textbox', { name: /cron expression/i }),
      '0 0 12 * * ?',
    );
    await user.click(saveButton(dialog));
    expect(await within(dialog).findByText('Message is required')).toBeInTheDocument();

    await user.type(within(dialog).getByRole('textbox', { name: /message/i }), 'fixed');
    expect(within(dialog).queryByText('Message is required')).not.toBeInTheDocument();
    await user.click(saveButton(dialog));

    await expectDialogClosed();
    expect(backend.writes()).toHaveLength(1);
  });
});

describe('switching inside the form', () => {
  test('switching task removes the old task inputs, and they are not sent', async () => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);

    await chooseTask(user, dialog, 'Log Task');
    await user.type(within(dialog).getByRole('textbox', { name: /message/i }), 'left behind');

    await chooseTask(user, dialog, 'Dummy Email Task');
    expect(within(dialog).queryByRole('textbox', { name: /message/i })).not.toBeInTheDocument();

    await user.type(within(dialog).getByRole('textbox', { name: /^to/i }), 'a@example.com');
    await user.type(within(dialog).getByRole('textbox', { name: /^subject/i }), 'Hi');
    await chooseTimingKind(user, dialog, 'Cron');
    await user.type(
      within(dialog).getByRole('textbox', { name: /cron expression/i }),
      '0 0 12 * * ?',
    );
    await user.click(saveButton(dialog));

    await expectDialogClosed();
    const [write] = backend.writes();
    expect(write.body).toEqual({
      taskKey: 'email',
      timing: { type: 'CRON', expression: '0 0 12 * * ?' },
      timeZone: TIME_ZONE,
      parameters: { to: 'a@example.com', subject: 'Hi' },
    });
  });

  test('switching timing kind removes the old fields, and they are not sent', async () => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);

    await chooseTask(user, dialog, 'DB Query Task');
    expect(within(dialog).getByLabelText(/date and time/i)).toBeInTheDocument();

    await chooseTimingKind(user, dialog, 'Every X');
    expect(within(dialog).queryByLabelText(/date and time/i)).not.toBeInTheDocument();
    await user.type(within(dialog).getByRole('spinbutton', { name: /every/i }), '15');

    await chooseTimingKind(user, dialog, 'Weekly');
    expect(within(dialog).queryByRole('spinbutton', { name: /every/i })).not.toBeInTheDocument();
    await user.click(within(dialog).getByRole('checkbox', { name: 'Friday' }));
    setInputValue(within(dialog).getByLabelText(/^time/i), '18:45');

    await chooseTimingKind(user, dialog, 'Cron');
    expect(within(dialog).queryByRole('checkbox')).not.toBeInTheDocument();
    expect(within(dialog).queryByLabelText(/^time/i)).not.toBeInTheDocument();
    await user.type(
      within(dialog).getByRole('textbox', { name: /cron expression/i }),
      '0 0 12 * * ?',
    );
    await user.click(saveButton(dialog));

    await expectDialogClosed();
    const [write] = backend.writes();
    expect(write.body).toEqual({
      taskKey: 'db-query',
      // Nothing from the other three kinds leaks into the request.
      timing: { type: 'CRON', expression: '0 0 12 * * ?' },
      timeZone: TIME_ZONE,
      parameters: {},
    });
  });

  test('a hidden kind that was left invalid does not block saving', async () => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);

    await chooseTask(user, dialog, 'DB Query Task');
    await chooseTimingKind(user, dialog, 'Every X');
    await user.click(saveButton(dialog));
    expect(await within(dialog).findByText('Enter a whole number, 1 or more')).toBeInTheDocument();

    await chooseTimingKind(user, dialog, 'Cron');
    await user.type(
      within(dialog).getByRole('textbox', { name: /cron expression/i }),
      '0 0 12 * * ?',
    );
    await user.click(saveButton(dialog));

    await expectDialogClosed();
    expect(backend.writes()).toHaveLength(1);
  });
});

describe('while saving', () => {
  test('the save button is disabled, so the schedule cannot be saved twice', async () => {
    let release: () => void = () => {};
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.post('/api/schedules', async () => {
        await gate;
        return HttpResponse.json(
          {
            id: 'new-1',
            taskKey: 'db-query',
            timing: { type: 'CRON', expression: '0 0 12 * * ?' },
            timeZone: TIME_ZONE,
            parameters: {},
            createdAt: '2026-11-25T09:00:00Z',
            updatedAt: '2026-11-25T09:00:00Z',
          },
          { status: 201 },
        );
      }),
    );

    const { user } = renderPage();
    const dialog = await openAddDialog(user);
    await chooseTask(user, dialog, 'DB Query Task');
    await chooseTimingKind(user, dialog, 'Cron');
    await user.type(
      within(dialog).getByRole('textbox', { name: /cron expression/i }),
      '0 0 12 * * ?',
    );
    await user.click(saveButton(dialog));

    const saving = await within(dialog).findByRole('button', { name: 'Saving…' });
    expect(saving).toBeDisabled();
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toBeDisabled();
    // Neither a click on the disabled button nor Enter in a field sends a second request.
    fireEvent.click(saving);
    await user.type(within(dialog).getByRole('textbox', { name: /cron expression/i }), '{Enter}');
    expect(backend.writes()).toHaveLength(1);

    release();
    await expectDialogClosed();
    expect(backend.writes()).toHaveLength(1);
  });

  test('cancel closes the form without saving anything', async () => {
    const { user } = renderPage();
    const dialog = await openAddDialog(user);
    await chooseTask(user, dialog, 'Log Task');
    await user.type(within(dialog).getByRole('textbox', { name: /message/i }), 'never mind');

    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));

    await expectDialogClosed();
    expect(backend.writes()).toEqual([]);
    expect(screen.getByText('No schedules yet')).toBeInTheDocument();
  });
});
