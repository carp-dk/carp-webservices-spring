-- CoreParticipantRepository.addRecruitment() does a check-then-insert (find existing recruitment for the
-- study, then insert if none exists) with no row to lock (there's nothing to create yet) and no unique
-- constraint backing it, so two concurrent calls to create the first recruitment for the same study could
-- both pass the null-check and both insert - producing two recruitment rows for one study.
--
-- Any environment that already hit this race has a duplicate pair sitting in `recruitments` today, which
-- would make the CREATE UNIQUE INDEX below fail outright. The race can only happen at initial creation (the
-- check-then-insert only runs when no recruitment exists yet), so the losing row is never referenced again -
-- append()/updateRecruitment() both look it up by studyId and consistently land on one of the two, leaving
-- the other empty forever. Delete only that provably-safe case (a duplicate with no participant/group rows
-- of its own) before creating the index; a duplicate that somehow does carry data is left alone and will
-- surface as a loud constraint violation below instead of being silently guessed at.
DELETE FROM recruitments dup
    USING recruitments keep
WHERE dup.id > keep.id
  AND (dup.snapshot -> 'studyId') = (keep.snapshot -> 'studyId')
  AND NOT EXISTS (SELECT 1 FROM recruitment_participants rp WHERE rp.recruitment_id = dup.id)
  AND NOT EXISTS (SELECT 1 FROM recruitment_participant_groups rpg WHERE rpg.recruitment_id = dup.id);

-- The `recruitments` table has no dedicated study_id column, only a JSONB `snapshot` blob (see
-- RecruitmentNormalizer: the envelope keeps studyId as a top-level key even after normalization strips
-- participants/groups out). A unique index on that JSONB path lets Postgres itself reject the race - the
-- losing INSERT gets a constraint violation, which Spring Data JPA translates into a
-- DataIntegrityViolationException, already mapped to 409 by ExceptionAdvices.handleConflict with no
-- application code changes needed.
CREATE UNIQUE INDEX idx_recruitments_study_id ON recruitments ((snapshot -> 'studyId'));
