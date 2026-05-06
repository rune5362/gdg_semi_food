CREATE TABLE rpa_config (
    id BIGINT PRIMARY KEY,
    is_auto_run_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    run_times VARCHAR(255) NOT NULL DEFAULT '07:00,19:00',
    updated_at DATETIME NOT NULL
);

INSERT INTO rpa_config (id, is_auto_run_enabled, run_times, updated_at)
VALUES (1, false, '07:00,19:00', NOW());
