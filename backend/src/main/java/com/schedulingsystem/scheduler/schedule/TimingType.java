package com.schedulingsystem.scheduler.schedule;

/** The four ways a schedule can be timed. Stored in the {@code timing_type} column. */
public enum TimingType {
    ONE_TIME,
    INTERVAL,
    WEEKLY,
    CRON
}
