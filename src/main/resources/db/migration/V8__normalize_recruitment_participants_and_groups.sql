-- Normalizes the two unbounded maps inside recruitments.snapshot (participants, participantGroups)
-- into typed tables. ADDITIVE ONLY: recruitments.snapshot stays authoritative during the transition
-- (shadow-verify -> dual-write -> cutover -> later drop). See docs/participant-group-normalization.md.
--
-- The recruitment envelope (id/version/createdOn/studyId/studyProtocol/invitation) remains in
-- recruitments.snapshot; only the two maps are normalized here. study_id is denormalized onto the
-- child tables so participant/group queries need no join back to recruitments.

-- pg_trgm powers the ILIKE '%term%' participant search (btree cannot; leading-wildcard contains).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE recruitment_participants (
    id BIGSERIAL PRIMARY KEY,
    recruitment_id INTEGER NOT NULL REFERENCES recruitments (id) ON DELETE CASCADE,
    study_id VARCHAR(255) NOT NULL,
    participant_id VARCHAR(255) NOT NULL,
    account_identity_type VARCHAR(20) NOT NULL,   -- 'email' | 'username'
    username VARCHAR(255),
    email_address VARCHAR(255),
    sort_order INTEGER NOT NULL DEFAULT 0,          -- deterministic pagination; core set is unordered
    created_at TIMESTAMP WITHOUT TIME ZONE,
    created_by VARCHAR(255),
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    updated_by VARCHAR(255),
    CONSTRAINT recruitment_participants_study_participant_unique UNIQUE (study_id, participant_id),
    -- Identity is exactly one of email/username; the matching column is set and the other is null.
    -- Rejects unknown types and contradictory rows (email type with null email, etc.).
    CONSTRAINT recruitment_participants_identity_valid CHECK (
        (account_identity_type = 'email' AND email_address IS NOT NULL AND username IS NULL)
        OR (account_identity_type = 'username' AND username IS NOT NULL AND email_address IS NULL)
    )
);

CREATE INDEX recruitment_participants_recruitment_idx
    ON recruitment_participants (recruitment_id);
CREATE INDEX recruitment_participants_study_idx
    ON recruitment_participants (study_id);
CREATE INDEX recruitment_participants_username_trgm_idx
    ON recruitment_participants USING GIN (username gin_trgm_ops);
CREATE INDEX recruitment_participants_email_trgm_idx
    ON recruitment_participants USING GIN (email_address gin_trgm_ops);

CREATE TABLE recruitment_participant_groups (
    id BIGSERIAL PRIMARY KEY,
    recruitment_id INTEGER NOT NULL REFERENCES recruitments (id) ON DELETE CASCADE,
    study_id VARCHAR(255) NOT NULL,
    group_id VARCHAR(255) NOT NULL,                 -- == study_deployment_id once deployed
    is_deployed BOOLEAN NOT NULL DEFAULT FALSE,
    name VARCHAR(255),                              -- representation.name (nullable)
    created_at TIMESTAMP WITHOUT TIME ZONE,
    created_by VARCHAR(255),
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    updated_by VARCHAR(255),
    CONSTRAINT recruitment_participant_groups_group_id_unique UNIQUE (group_id)
);

CREATE INDEX recruitment_participant_groups_recruitment_idx
    ON recruitment_participant_groups (recruitment_id);
CREATE INDEX recruitment_participant_groups_study_idx
    ON recruitment_participant_groups (study_id);

CREATE TABLE recruitment_participant_group_members (
    study_id VARCHAR(255) NOT NULL,                 -- denormalized; enables the participant FK below
    group_id VARCHAR(255) NOT NULL
        REFERENCES recruitment_participant_groups (group_id) ON DELETE CASCADE,
    participant_id VARCHAR(255) NOT NULL,
    assigned_all BOOLEAN NOT NULL,                  -- AssignedTo.All (true) vs AssignedTo.Roles
    role_names TEXT[],                              -- AssignedTo.Roles.roleNames when not assigned_all
    PRIMARY KEY (group_id, participant_id),
    -- A member must reference a real participant in the same study (uses the UNIQUE above).
    CONSTRAINT recruitment_participant_group_members_participant_fkey
        FOREIGN KEY (study_id, participant_id)
        REFERENCES recruitment_participants (study_id, participant_id) ON DELETE CASCADE,
    -- AssignedTo.All has no role names; AssignedTo.Roles carries them.
    CONSTRAINT recruitment_participant_group_members_roles_valid CHECK (
        (assigned_all AND role_names IS NULL) OR (NOT assigned_all AND role_names IS NOT NULL)
    )
);

-- "which groups is this participant assigned to" (relational replacement for the JSONB lateral join).
CREATE INDEX recruitment_participant_group_members_participant_idx
    ON recruitment_participant_group_members (participant_id);
