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
    updated_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_schedules_created_at ON schedules (created_at DESC);
