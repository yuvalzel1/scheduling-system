package com.schedulingsystem.scheduler.schedule;

import java.util.UUID;

/**
 * Where a schedule's alarm is set and removed.
 *
 * <p>The schedule logic depends on this, not on Quartz: everything that knows Quartz lives in the
 * {@code schedule.quartz} package and plugs in here.
 */
public interface ScheduleAlarms {

    /** Sets the alarm for a new schedule, or replaces the alarm of a changed one. */
    void register(Schedule schedule);

    /** Removes the alarm. Does nothing when there is none. */
    void unregister(UUID scheduleId);
}
