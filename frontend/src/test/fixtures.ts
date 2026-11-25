import type { Schedule, TaskDefinition } from '../api/types';

/** The three tasks the real backend offers. */
export const TASKS: TaskDefinition[] = [
  {
    key: 'log',
    name: 'Log Task',
    description: 'Writes a message to the application log',
    parameters: [
      {
        name: 'message',
        label: 'Message',
        type: 'STRING',
        required: true,
        description: 'The text to write to the log',
      },
    ],
  },
  {
    key: 'email',
    name: 'Dummy Email Task',
    description: 'Pretends to send an email',
    parameters: [
      { name: 'to', label: 'To', type: 'STRING', required: true, description: 'Recipient' },
      { name: 'subject', label: 'Subject', type: 'STRING', required: true },
      { name: 'body', label: 'Body', type: 'STRING', required: false },
    ],
  },
  {
    key: 'db-query',
    name: 'DB Query Task',
    description: 'Reads the newest schedules and logs how many there are',
    parameters: [
      {
        name: 'limit',
        label: 'Limit',
        type: 'NUMBER',
        required: false,
        description: 'How many schedules to read',
      },
    ],
  },
];

const STAMPS = { createdAt: '2026-11-01T10:00:00Z', updatedAt: '2026-11-01T10:00:00Z' };

export const ONE_TIME_LOG: Schedule = {
  id: 'sched-one-time',
  taskKey: 'log',
  // 08:00 in New York (summer time), the time zone the tests run in.
  timing: { type: 'ONE_TIME', runAt: '2099-06-01T12:00:00Z' },
  timeZone: 'America/New_York',
  parameters: { message: 'hello' },
  ...STAMPS,
};

export const INTERVAL_DB_QUERY: Schedule = {
  id: 'sched-interval',
  taskKey: 'db-query',
  timing: { type: 'INTERVAL', every: 15, unit: 'HOURS' },
  timeZone: 'America/New_York',
  parameters: { limit: 5 },
  ...STAMPS,
};

export const WEEKLY_EMAIL: Schedule = {
  id: 'sched-weekly',
  taskKey: 'email',
  timing: { type: 'WEEKLY', days: ['MONDAY', 'FRIDAY'], time: '18:45' },
  timeZone: 'America/New_York',
  parameters: { to: 'team@example.com', subject: 'Weekly report', body: 'See attached' },
  ...STAMPS,
};

export const CRON_LOG: Schedule = {
  id: 'sched-cron',
  taskKey: 'log',
  timing: { type: 'CRON', expression: '0 0/5 * * * ?' },
  timeZone: 'America/New_York',
  parameters: { message: 'tick' },
  ...STAMPS,
};
