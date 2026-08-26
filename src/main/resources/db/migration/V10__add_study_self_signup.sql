-- Kahoot-style semi-self-signup: a study admin enables self-signup and gets back a short code; anyone with
-- the code can synchronously create their own anonymous account/deployment/participant. Disabled by default.
--
-- One row per study; created on the first "enable" call and never deleted. Ending self-signup only flips
-- `enabled` to false — `short_code` and `current_participant_count` survive an enable -> end -> re-enable
-- cycle, so a previously printed/scanned QR code keeps working if re-enabled (see SelfSignupServiceImpl).
--
-- `current_participant_count` is a LIFETIME counter against `max_participants` (never reset by end/re-
-- enable) and is the only mutable counter here; it's incremented atomically when a reservation is
-- finalized (see SelfSignupReservationStore.finalize, added in V11) to enforce the cap correctly under
-- concurrent signups. There is
-- deliberately no CHECK tying it to max_participants: an admin may lower max_participants below the
-- current count (e.g. after raising it and changing their mind), and a hard constraint would then block
-- that otherwise-unrelated update.
--
-- participant_role_name/client_id/redirect_uri/subdomain/expiration_seconds are supplied once by the admin
-- at enable-time (the same fields AnonymousParticipantRequest already needs for CSV export) rather than by
-- the public signup caller, which would be an obvious abuse vector.
CREATE TABLE study_self_signup (
    id                         BIGSERIAL PRIMARY KEY,
    study_id                   VARCHAR(255) NOT NULL UNIQUE,
    short_code                 VARCHAR(5)   NOT NULL UNIQUE,
    enabled                    BOOLEAN      NOT NULL DEFAULT FALSE,
    participant_role_name      VARCHAR(255) NOT NULL,
    max_participants           INT          NOT NULL,
    current_participant_count  INT          NOT NULL DEFAULT 0,
    client_id                  VARCHAR(255) NOT NULL,
    redirect_uri               VARCHAR(2048),
    subdomain                  VARCHAR(255),
    expiration_seconds         BIGINT       NOT NULL DEFAULT 86400,
    created_at                 TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at                 TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT chk_study_self_signup_max_positive CHECK (max_participants > 0)
);

-- Public lookup is always "find by short code"; short_code is already UNIQUE, giving us a btree index for
-- free, so no extra index is added for it.

-- Basic per-IP rate limiting for the public signup endpoint. Fixed 1-minute windows (not a true sliding
-- window - see SelfSignupRateLimitStore); one row per (ip, window).
CREATE TABLE self_signup_rate_limit (
    ip_address    VARCHAR(45) NOT NULL,
    window_start  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    request_count INT NOT NULL DEFAULT 1,
    PRIMARY KEY (ip_address, window_start)
);

-- Supports the periodic sweep that deletes windows older than the retention horizon (see
-- SelfSignupRateLimitStore.deleteOlderThan), so the table doesn't grow unbounded.
CREATE INDEX idx_self_signup_rate_limit_window_start ON self_signup_rate_limit (window_start);
