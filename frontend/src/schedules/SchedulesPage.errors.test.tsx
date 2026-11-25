import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, test } from 'vitest';
import type { FieldError } from '../api/types';
import { backend, server } from '../test/fakeBackend';
import { CRON_LOG } from '../test/fixtures';
import {
  chooseTask,
  chooseTimingKind,
  expectDialogClosed,
  openAddDialog,
  openEditDialog,
  renderPage,
  saveButton,
} from '../test/render';
import type { UserEvent } from '@testing-library/user-event';

function validationFailure(errors: FieldError[]) {
  return HttpResponse.json(
    {
      type: 'about:blank',
      title: 'Validation failed',
      status: 400,
      detail: 'The schedule is not valid',
      instance: '/api/schedules',
      errors,
    },
    { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
  );
}

/** Fills a form that passes the checks in the browser: Log Task, cron, a message. */
async function fillValidForm(user: UserEvent, dialog: HTMLElement) {
  await chooseTask(user, dialog, 'Log Task');
  await chooseTimingKind(user, dialog, 'Cron');
  await user.type(within(dialog).getByRole('textbox', { name: /cron expression/i }), '0 0 12 * *');
  await user.type(within(dialog).getByRole('textbox', { name: /message/i }), 'hello');
}

describe('errors from the server', () => {
  test('a field error shows under that field, and the dialog stays open with the data', async () => {
    server.use(
      http.post('/api/schedules', () =>
        validationFailure([
          { field: 'timing.expression', message: 'Cron expression must have 6 or 7 parts' },
        ]),
      ),
    );
    const { user } = renderPage();
    const dialog = await openAddDialog(user);
    await fillValidForm(user, dialog);

    await user.click(saveButton(dialog));

    const cron = within(dialog).getByRole('textbox', { name: /cron expression/i });
    expect(
      await within(dialog).findByText(/Cron expression must have 6 or 7 parts/),
    ).toBeInTheDocument();
    expect(cron).toBeInvalid();
    expect(cron).toHaveAccessibleDescription(/Cron expression must have 6 or 7 parts/);
    // No general error: the message belongs to the field.
    expect(within(dialog).queryByRole('alert')).not.toBeInTheDocument();

    // Dialog still open, nothing the user typed is lost, and saving is possible again.
    expect(screen.getByRole('dialog', { name: 'Add schedule' })).toBeInTheDocument();
    expect(cron).toHaveValue('0 0 12 * *');
    expect(within(dialog).getByRole('textbox', { name: /message/i })).toHaveValue('hello');
    expect(saveButton(dialog)).toBeEnabled();
    expect(screen.queryByText('Schedule saved')).not.toBeInTheDocument();
  });

  test('a task input error shows under that input', async () => {
    server.use(
      http.post('/api/schedules', () =>
        validationFailure([{ field: 'parameters.message', message: 'Message is too long' }]),
      ),
    );
    const { user } = renderPage();
    const dialog = await openAddDialog(user);
    await fillValidForm(user, dialog);

    await user.click(saveButton(dialog));

    const message = within(dialog).getByRole('textbox', { name: /message/i });
    expect(await within(dialog).findByText('Message is too long')).toBeInTheDocument();
    expect(message).toBeInvalid();
    expect(message).toHaveAccessibleDescription('Message is too long');
  });

  test('several field errors each land on their own field', async () => {
    server.use(
      http.post('/api/schedules', () =>
        validationFailure([
          { field: 'timing.expression', message: 'Cron expression is not valid' },
          { field: 'parameters.message', message: 'Message is too long' },
          { field: 'taskKey', message: 'Task is no longer available' },
        ]),
      ),
    );
    const { user } = renderPage();
    const dialog = await openAddDialog(user);
    await fillValidForm(user, dialog);

    await user.click(saveButton(dialog));

    expect(await within(dialog).findByText(/Cron expression is not valid/)).toBeInTheDocument();
    expect(within(dialog).getByText('Message is too long')).toBeInTheDocument();
    expect(within(dialog).getByText('Task is no longer available')).toBeInTheDocument();
  });

  test('an error for something the form does not show appears at the top instead of being lost', async () => {
    server.use(
      http.post('/api/schedules', () =>
        validationFailure([{ field: 'timeZone', message: 'Unknown time zone' }]),
      ),
    );
    const { user } = renderPage();
    const dialog = await openAddDialog(user);
    await fillValidForm(user, dialog);

    await user.click(saveButton(dialog));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Unknown time zone');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  test('fixing the field and saving again clears the error and saves', async () => {
    server.use(
      http.post(
        '/api/schedules',
        () => validationFailure([{ field: 'timing.expression', message: 'Not a valid cron' }]),
        { once: true },
      ),
    );
    const { user } = renderPage();
    const dialog = await openAddDialog(user);
    await fillValidForm(user, dialog);
    await user.click(saveButton(dialog));
    expect(await within(dialog).findByText(/Not a valid cron/)).toBeInTheDocument();

    await user.type(within(dialog).getByRole('textbox', { name: /cron expression/i }), ' ?');
    await user.click(saveButton(dialog));

    await expectDialogClosed();
    expect(backend.writes()).toHaveLength(2);
    expect(await screen.findByRole('row', { name: /Cron: 0 0 12 \* \* \?/ })).toBeInTheDocument();
  });

  test('a server failure shows a message at the top of the dialog and keeps the data', async () => {
    server.use(
      http.post('/api/schedules', () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Internal Server Error', status: 500 },
          { status: 500 },
        ),
      ),
    );
    const { user } = renderPage();
    const dialog = await openAddDialog(user);
    await fillValidForm(user, dialog);

    await user.click(saveButton(dialog));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Could not save the schedule. Please try again.',
    );
    expect(screen.getByRole('dialog', { name: 'Add schedule' })).toBeInTheDocument();
    expect(within(dialog).getByRole('textbox', { name: /message/i })).toHaveValue('hello');
    expect(saveButton(dialog)).toBeEnabled();
  });

  test('a server failure with an explanation shows that explanation', async () => {
    backend.schedules = [CRON_LOG];
    server.use(
      http.put('/api/schedules/:id', () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Not Found', status: 404, detail: 'Schedule not found' },
          { status: 404 },
        ),
      ),
    );
    const { user } = renderPage();
    const dialog = await openEditDialog(user, /Log Task/);

    await user.click(saveButton(dialog));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Schedule not found');
  });

  test('when the server cannot be reached, says so at the top of the dialog', async () => {
    server.use(http.post('/api/schedules', () => HttpResponse.error()));
    const { user } = renderPage();
    const dialog = await openAddDialog(user);
    await fillValidForm(user, dialog);

    await user.click(saveButton(dialog));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Could not reach the server. Please try again.',
    );
    expect(within(dialog).getByRole('textbox', { name: /message/i })).toHaveValue('hello');
  });

  test('the general error goes away on the next successful save', async () => {
    server.use(http.post('/api/schedules', () => HttpResponse.error(), { once: true }));
    const { user } = renderPage();
    const dialog = await openAddDialog(user);
    await fillValidForm(user, dialog);
    await user.click(saveButton(dialog));
    expect(await within(dialog).findByRole('alert')).toBeInTheDocument();

    await user.click(saveButton(dialog));

    await expectDialogClosed();
    expect(await screen.findByText('Schedule saved')).toBeInTheDocument();
  });
});

describe('when the task list fails to load', () => {
  test('the form says so instead of showing an empty dropdown, and can retry', async () => {
    server.use(http.get('/api/tasks', () => HttpResponse.json({ status: 500 }, { status: 500 })));
    const { user } = renderPage();
    await screen.findByText('No schedules yet');

    await user.click(screen.getByRole('button', { name: 'Add schedule' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add schedule' });

    const alert = await within(dialog).findByRole('alert');
    expect(alert).toHaveTextContent('Could not load the task list.');
    expect(within(dialog).queryByRole('combobox')).not.toBeInTheDocument();
    expect(saveButton(dialog)).toBeDisabled();

    // The server recovers.
    server.resetHandlers();
    await user.click(within(alert).getByRole('button', { name: 'Retry' }));

    expect(await within(dialog).findByRole('combobox', { name: /^task/i })).toBeInTheDocument();
    expect(within(dialog).queryByRole('alert')).not.toBeInTheDocument();
    expect(saveButton(dialog)).toBeEnabled();
  });
});
