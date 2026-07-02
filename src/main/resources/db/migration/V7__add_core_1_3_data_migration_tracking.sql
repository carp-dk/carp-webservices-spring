CREATE TABLE core_data_migration_runs (
    id BIGSERIAL PRIMARY KEY,
    migration_name VARCHAR(100) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    last_deployment_id INTEGER NOT NULL DEFAULT 0,
    last_recruitment_id INTEGER NOT NULL DEFAULT 0,
    processed_count BIGINT NOT NULL DEFAULT 0,
    migrated_count BIGINT NOT NULL DEFAULT 0,
    failure_count BIGINT NOT NULL DEFAULT 0,
    report JSONB
);

CREATE INDEX core_data_migration_runs_lookup_idx
    ON core_data_migration_runs (migration_name, status, id DESC);

CREATE TABLE core_data_migration_failures (
    run_id BIGINT NOT NULL REFERENCES core_data_migration_runs(id) ON DELETE CASCADE,
    table_name VARCHAR(100) NOT NULL,
    row_id INTEGER NOT NULL,
    jsonb_size BIGINT NOT NULL,
    error TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (run_id, table_name, row_id)
);

CREATE TABLE core_data_migration_rows (
    run_id BIGINT NOT NULL REFERENCES core_data_migration_runs(id) ON DELETE CASCADE,
    table_name VARCHAR(100) NOT NULL,
    row_id INTEGER NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    duration_ms BIGINT NOT NULL,
    jsonb_size BIGINT NOT NULL,
    error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (run_id, table_name, row_id)
);

CREATE INDEX data_stream_sequence_stream_last_sequence_idx
    ON data_stream_sequence (data_stream_id, last_sequence_id DESC);

CREATE INDEX data_stream_ids_deployment_idx
    ON data_stream_ids (study_deployment_id);

CREATE INDEX recruitments_participant_groups_idx
    ON recruitments USING GIN ((snapshot->'participantGroups'));
