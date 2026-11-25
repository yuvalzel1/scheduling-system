import { QueryClient } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent, { type UserEvent } from '@testing-library/user-event';
import { expect } from 'vitest';
import { AppProviders } from '../AppProviders';
import { SchedulesPage } from '../schedules/SchedulesPage';

/** Renders the whole page against the fake backend, with a fresh cache and no retries. */
export function renderPage(): { user: UserEvent } {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <AppProviders queryClient={queryClient}>
      <SchedulesPage />
    </AppProviders>,
  );
  return { user: userEvent.setup() };
}

/** Opens the add dialog and waits until the task list has loaded. */
export async function openAddDialog(user: UserEvent): Promise<HTMLElement> {
  await user.click(screen.getByRole('button', { name: 'Add schedule' }));
  const dialog = await screen.findByRole('dialog', { name: 'Add schedule' });
  await within(dialog).findByRole('combobox', { name: /^task/i });
  return dialog;
}

/** Opens the edit dialog for the row whose text matches, and waits for the task list. */
export async function openEditDialog(user: UserEvent, rowName: RegExp): Promise<HTMLElement> {
  const row = await screen.findByRole('row', { name: rowName });
  await user.click(within(row).getByRole('button', { name: 'Edit schedule' }));
  const dialog = await screen.findByRole('dialog', { name: 'Edit schedule' });
  await within(dialog).findByRole('combobox', { name: /^task/i });
  return dialog;
}

/** Picks an option in an MUI select and waits for its menu to close. */
export async function chooseOption(
  user: UserEvent,
  dialog: HTMLElement,
  selectName: RegExp,
  optionName: string,
): Promise<void> {
  await user.click(within(dialog).getByRole('combobox', { name: selectName }));
  await user.click(await screen.findByRole('option', { name: optionName }));
  await waitFor(() => expect(screen.queryByRole('listbox')).not.toBeInTheDocument());
}

export function chooseTask(user: UserEvent, dialog: HTMLElement, taskName: string): Promise<void> {
  return chooseOption(user, dialog, /^task/i, taskName);
}

export async function chooseTimingKind(
  user: UserEvent,
  dialog: HTMLElement,
  kind: 'One time' | 'Every X' | 'Weekly' | 'Cron',
): Promise<void> {
  await user.click(within(dialog).getByRole('radio', { name: kind }));
}

/**
 * Sets a date-time or time input. user-event cannot type into datetime-local inputs, so the
 * value is set the way a browser picker would set it.
 */
export function setInputValue(input: HTMLElement, value: string): void {
  fireEvent.change(input, { target: { value } });
}

export function saveButton(dialog: HTMLElement): HTMLElement {
  return within(dialog).getByRole('button', { name: /^sav/i });
}

export async function expectDialogClosed(): Promise<void> {
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
}
