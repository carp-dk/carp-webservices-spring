-- Durable, TTL-bounded holds on self-signup capacity, claimed BEFORE calling Keycloak and finalized
-- afterward - see SelfSignupReservationStore. This closes two related problems with checking capacity
-- only after account creation: (1) under a burst beyond capacity, every caller would mint a real
-- Keycloak account before only the allowed ones actually got seated, wasting the rest as orphans; (2) a
-- crash between account creation and finalizing/recording it could leave an account with no trace at all.
--
-- Claiming a reservation here happens instead, atomically, before any Keycloak call: study_self_signup's
-- confirmed count plus this table's still-live (non-expired) reservations for the study are checked
-- together against max_participants, so a caller only proceeds to Keycloak if a real slot exists.
--
-- A reservation is a HOLD, not a commitment: `expires_at` bounds how long it counts against the cap. If
-- the holder crashes or never finalizes it, the reservation simply stops counting once it expires (claim
-- and finalize only ever consider `expires_at > now()` rows) - capacity is never permanently consumed by
-- an interrupted request. SelfSignupReservationCleanupJob periodically deletes expired rows, but it is
-- NOT mere table hygiene: an expired, still-present row is the only durable evidence that a signup
-- attempt may have created a Keycloak account this process never got to record anywhere, so the job
-- reconciles it into the anonymous-account cleanup ledger before deleting it. See the job's own class doc.
CREATE TABLE self_signup_reservation (
    id         VARCHAR(255) NOT NULL PRIMARY KEY,
    study_id   VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

-- Supports tryClaim's "how many live reservations does this study have" count.
CREATE INDEX idx_self_signup_reservation_study_id_expires_at
    ON self_signup_reservation (study_id, expires_at);

-- Supports the cleanup sweep's "which reservations are expired, across all studies" scan.
CREATE INDEX idx_self_signup_reservation_expires_at
    ON self_signup_reservation (expires_at);
