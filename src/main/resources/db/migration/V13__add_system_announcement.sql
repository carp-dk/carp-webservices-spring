-- A single system-wide announcement (e.g. "Maintenance tonight 20:00-22:00") that admins set via
-- PUT /api/admin/announcement and anyone can read via the public GET /api/announcement.
--
-- Singleton table: the CHECK pins the primary key to 1, so there is at most one row. "No row" means no
-- announcement is active; clearing it deletes the row. updated_by is the admin's account id.
CREATE TABLE system_announcement (
    id         SMALLINT     NOT NULL PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    message    TEXT         NOT NULL,
    severity   VARCHAR(16)  NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_by VARCHAR(255) NOT NULL
);
