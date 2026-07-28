-- Records, per study, when its fast-pipeline anonymous accounts become eligible for deletion.
--
-- All anonymous accounts generated via the fast pipeline for a study are members of a single Keycloak
-- group keyed by the study id (the carp-keycloak bulk extension does user.joinGroup(<study group>)).
-- This table holds one row per study; `delete_after` is the deletion timer and is EXTENDED to the latest
-- link expiry (plus a safety buffer) every time more accounts are generated ("reset the timer"). A
-- scheduled cleanup (Phase 2) deletes the study's Keycloak group members once now > delete_after and their
-- sessions have ended.
--
-- `delete_after` = latest link expiry + a safety buffer (applied by the writer), not the raw link expiry,
-- so cleanup never races link expiry, clock skew, or a late redemption.
--
-- One row per study => study_id is unique. account_count is cumulative (accounts generated in Keycloak,
-- including any the app skipped), for visibility only. last_attempted_at is the time cleanup last tried
-- this study; the sweep orders by it (nulls first) so a study that can't finish (e.g. an active session
-- keeps it non-empty) rotates to the back instead of starving the others.
CREATE TABLE anonymous_account_cleanup (
    id                BIGSERIAL PRIMARY KEY,
    study_id          VARCHAR(255)                NOT NULL UNIQUE,
    delete_after      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    account_count     BIGINT                      NOT NULL DEFAULT 0,
    last_attempted_at TIMESTAMP WITHOUT TIME ZONE,
    created_at        TIMESTAMP WITHOUT TIME ZONE,
    updated_at        TIMESTAMP WITHOUT TIME ZONE
);

-- Supports the Phase 2 cleanup scan for studies whose accounts are past their deletion time.
CREATE INDEX idx_anonymous_account_cleanup_delete_after ON anonymous_account_cleanup (delete_after);
