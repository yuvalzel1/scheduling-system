import { DAYS_OF_WEEK, type DayOfWeek, type Timing } from '../api/types';

export const DAY_SHORT_NAMES: Record<DayOfWeek, string> = {
  MONDAY: 'Mon',
  TUESDAY: 'Tue',
  WEDNESDAY: 'Wed',
  THURSDAY: 'Thu',
  FRIDAY: 'Fri',
  SATURDAY: 'Sat',
  SUNDAY: 'Sun',
};

export const DAY_FULL_NAMES: Record<DayOfWeek, string> = {
  MONDAY: 'Monday',
  TUESDAY: 'Tuesday',
  WEDNESDAY: 'Wednesday',
  THURSDAY: 'Thursday',
  FRIDAY: 'Friday',
  SATURDAY: 'Saturday',
  SUNDAY: 'Sunday',
};

interface DescribeOptions {
  /** Defaults to the browser's locale. */
  locale?: string;
  /** Defaults to the browser's time zone. Only used for one-time schedules. */
  timeZone?: string;
}

/** Keeps only hours and minutes, so both "08:30" and "08:30:00" read as "08:30". */
export function toHoursAndMinutes(time: string): string {
  return time.slice(0, 5);
}

function plural(count: number, singular: string): string {
  return count === 1 ? `Every ${singular}` : `Every ${count} ${singular}s`;
}

/** Turns a schedule's timing into a short sentence for the table. */
export function describeTiming(timing: Timing, options: DescribeOptions = {}): string {
  switch (timing.type) {
    case 'ONE_TIME': {
      const date = new Date(timing.runAt);
      if (Number.isNaN(date.getTime())) {
        return `Once at ${timing.runAt}`;
      }
      const formatted = new Intl.DateTimeFormat(options.locale, {
        dateStyle: 'medium',
        timeStyle: 'short',
        timeZone: options.timeZone,
      }).format(date);
      return `Once on ${formatted}`;
    }
    case 'INTERVAL':
      return plural(timing.every, timing.unit === 'HOURS' ? 'hour' : 'minute');
    case 'WEEKLY': {
      const time = toHoursAndMinutes(timing.time);
      const days = DAYS_OF_WEEK.filter((day) => timing.days.includes(day));
      if (days.length === DAYS_OF_WEEK.length) {
        return `Every day at ${time}`;
      }
      return `${days.map((day) => DAY_SHORT_NAMES[day]).join(', ')} at ${time}`;
    }
    case 'CRON':
      return `Cron: ${timing.expression}`;
  }
}
