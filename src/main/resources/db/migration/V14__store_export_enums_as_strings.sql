-- Export.status and Export.type were mapped without @Enumerated, so Hibernate stored their ordinals ('0', '1',
-- ...) in these varchar columns. Reordering an enum would silently change what existing rows mean - e.g. the
-- cleanup job keeps ANONYMOUS_PARTICIPANTS exports past retention, and that decision rested on type = '3'.
-- The entity now maps both as EnumType.STRING; convert existing rows to the constant names.
--
-- NULLs (rows that predate a column, or were never set) become 'UNKNOWN': the Kotlin properties are
-- non-nullable, and a NULL type made the cleanup job's `when (export.type)` throw.
UPDATE exports
SET status = CASE COALESCE(status, '0')
                 WHEN '0' THEN 'UNKNOWN'
                 WHEN '1' THEN 'IN_PROGRESS'
                 WHEN '2' THEN 'AVAILABLE'
                 WHEN '3' THEN 'ERROR'
                 WHEN '4' THEN 'EXPIRED'
                 ELSE status
             END,
    type   = CASE COALESCE(type, '0')
                 WHEN '0' THEN 'UNKNOWN'
                 WHEN '1' THEN 'STUDY_DATA'
                 WHEN '2' THEN 'DEPLOYMENT_DATA'
                 WHEN '3' THEN 'ANONYMOUS_PARTICIPANTS'
                 ELSE type
             END;
