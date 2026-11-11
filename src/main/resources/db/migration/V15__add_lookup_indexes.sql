-- Secondary indexes for lookups that were full table scans (issue #372, index half).
--
-- Aggregate tables keep their UUID inside the JSONB `snapshot`, and the repositories filter on the text form
-- (`snapshot->>'id'`), so these are expression indexes on exactly that form. An index on `snapshot->'id'`
-- (jsonb) would not be used: V12's idx_recruitments_study_id is one, which is why recruitments gets a second.
-- Without them every lookup also detoasts every snapshot it scans; measured on a 15k-deployment copy, a
-- deployment lookup went from 76 ms to 0.04 ms.
--
-- Plain (non-unique) indexes: protocols legitimately hold one row per version under the same id, and uniqueness
-- elsewhere is a separate decision that needs a duplicate audit first. Built without CONCURRENTLY: Flyway runs
-- before the application serves requests, so there is no live traffic to block.

-- Core aggregates, looked up by id on nearly every request touching them.
CREATE INDEX IF NOT EXISTS idx_deployments_snapshot_id ON deployments ((snapshot ->> 'id'));
CREATE INDEX IF NOT EXISTS idx_participant_groups_snapshot_study_deployment_id
    ON participant_groups ((snapshot ->> 'studyDeploymentId'));
CREATE INDEX IF NOT EXISTS idx_recruitments_snapshot_study_id ON recruitments ((snapshot ->> 'studyId'));
CREATE INDEX IF NOT EXISTS idx_studies_snapshot_id ON studies ((snapshot ->> 'id'));
CREATE INDEX IF NOT EXISTS idx_protocols_snapshot_id ON protocols ((snapshot ->> 'id'));

-- Per-participant tables, filtered by study/deployment (and name) in their repositories.
CREATE INDEX IF NOT EXISTS idx_consent_documents_deployment_id ON consent_documents (deployment_id);
CREATE INDEX IF NOT EXISTS idx_collections_study_id_name ON collections (study_id, name);
CREATE INDEX IF NOT EXISTS idx_collections_study_deployment_id_name ON collections (study_deployment_id, name);
CREATE INDEX IF NOT EXISTS idx_documents_collection_id_name ON documents (collection_id, name);
CREATE INDEX IF NOT EXISTS idx_files_study_id ON files (study_id);

-- Referencing side of collections.document_id -> documents.id: without it, deleting a document scans
-- collections to check the foreign key.
CREATE INDEX IF NOT EXISTS idx_collections_document_id ON collections (document_id);
