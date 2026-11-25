CREATE TABLE schedules
(
    id              UUID PRIMARY KEY,
    task_key        VARCHAR(100) NOT NULL,
    timing_type     VARCHAR(20)  NOT NULL
        CHECK (timing_type IN ('ONE_TIME', 'INTERVAL', 'WEEKLY', 'CRON')),
    run_at          TIMESTAMPTZ,
    interval_value  INTEGER,
    interval_unit   VARCHAR(20)
        CHECK (interval_unit IN ('MINUTES', 'HOURS')),
    weekly_days     VARCHAR(100),
    weekly_time     TIME,
    cron_expression VARCHAR(200),
    time_zone       VARCHAR(100) NOT NULL,
    parameters      JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,

    -- Each timing kind uses its own columns. The application already guarantees that only those
    -- are filled; this makes the database refuse anything else, whoever writes the row.
    --
    -- Every column is tested with IS NULL / IS NOT NULL on purpose: a CHECK passes when its
    -- expression is NULL, so "interval_value >= 1" alone would let a missing value through.
    CONSTRAINT schedules_timing_columns_match_type CHECK (
        (timing_type = 'ONE_TIME'
            AND run_at IS NOT NULL
            AND interval_value IS NULL AND interval_unit IS NULL
            AND weekly_days IS NULL AND weekly_time IS NULL
            AND cron_expression IS NULL)
        OR
        (timing_type = 'INTERVAL'
            AND interval_value IS NOT NULL AND interval_value >= 1 AND interval_unit IS NOT NULL
            AND run_at IS NULL
            AND weekly_days IS NULL AND weekly_time IS NULL
            AND cron_expression IS NULL)
        OR
        (timing_type = 'WEEKLY'
            AND weekly_days IS NOT NULL AND weekly_days <> '' AND weekly_time IS NOT NULL
            AND run_at IS NULL
            AND interval_value IS NULL AND interval_unit IS NULL
            AND cron_expression IS NULL)
        OR
        (timing_type = 'CRON'
            AND cron_expression IS NOT NULL AND cron_expression <> ''
            AND run_at IS NULL
            AND interval_value IS NULL AND interval_unit IS NULL
            AND weekly_days IS NULL AND weekly_time IS NULL)
    )
);

CREATE INDEX idx_schedules_created_at ON schedules (created_at DESC);

-- One row per finished run of a schedule's task: the run history shown in the UI.
CREATE TABLE executions
(
    id            UUID PRIMARY KEY,
    -- Deleting a schedule deletes its history with it.
    schedule_id   UUID         NOT NULL REFERENCES schedules (id) ON DELETE CASCADE,
    -- Which task ran. Kept here because a schedule can later be changed to another task.
    task_key      VARCHAR(100) NOT NULL,
    status        VARCHAR(20)  NOT NULL
        CHECK (status IN ('SUCCEEDED', 'FAILED')),
    started_at    TIMESTAMPTZ  NOT NULL,
    finished_at   TIMESTAMPTZ  NOT NULL,
    error_message TEXT,

    CONSTRAINT executions_finish_after_start CHECK (finished_at >= started_at),
    -- A failed run always says why; a successful one never carries an error.
    CONSTRAINT executions_error_only_when_failed CHECK ((status = 'FAILED') = (error_message IS NOT NULL))
);

CREATE INDEX idx_executions_schedule_newest ON executions (schedule_id, started_at DESC);
