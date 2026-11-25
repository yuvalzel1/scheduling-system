interface FormatOptions {
  /** Defaults to the browser's locale. */
  locale?: string;
  /** Defaults to the browser's time zone. */
  timeZone?: string;
}

/** A moment with seconds, in local time: runs can be seconds apart. */
export function formatMoment(isoInstant: string, options: FormatOptions = {}): string {
  const date = new Date(isoInstant);
  if (Number.isNaN(date.getTime())) {
    return isoInstant;
  }
  return new Intl.DateTimeFormat(options.locale, {
    dateStyle: 'medium',
    timeStyle: 'medium',
    timeZone: options.timeZone,
  }).format(date);
}

/** How long a run took, in the largest unit that reads well: "40 ms", "1.5 s", "2 min 5 s". */
export function formatDuration(startedAt: string, finishedAt: string): string {
  const ms = new Date(finishedAt).getTime() - new Date(startedAt).getTime();
  if (Number.isNaN(ms) || ms < 0) {
    return '–';
  }
  if (ms < 1) {
    return 'under 1 ms';
  }
  // Each step rounds first and only then picks the unit, so a value just under a boundary
  // moves up to the next unit instead of showing "1000 ms", "60.0 s" or "1 min 60 s".
  const wholeMs = Math.round(ms);
  if (wholeMs < 1000) {
    return `${wholeMs} ms`;
  }
  const tenthsOfSeconds = Math.round(ms / 100);
  if (tenthsOfSeconds < 600) {
    return `${(tenthsOfSeconds / 10).toFixed(1)} s`;
  }
  const totalSeconds = Math.round(ms / 1000);
  return `${Math.floor(totalSeconds / 60)} min ${totalSeconds % 60} s`;
}
