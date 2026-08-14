# Next DB Migration Notes

This file is a handoff for the next database refactor or table-splitting migration.

It captures what should be reused from the CARP Core 1.3 migration work, what was learned during testing, and what should be decided explicitly before implementing the next migration.

## Reuse from the Core 1.3 migration

The current migration approach is a good default template:

- explicit modes:
  - `inventory`
  - `dry-run`
  - `apply`
  - `verify`
- resumable processing
- bounded batches using primary-key pagination
- small transactions per batch
- idempotent row transformations
- database tracking of migration runs
- database tracking of per-row failures
- separate verification after apply

These properties are worth preserving unless the next migration has a strong reason to differ.

## Operational findings to reuse

### Primary execution path

Use Docker or Compose as the primary execution path.

Reason:

- it matches the production runtime more closely
- it avoids local terminal and profile differences
- it reduces false failures caused by local startup wiring

Local `bootRun` can still be useful as a fallback for local-only testing, but it should not be the main operational path.

### Rollout pattern

Use the same staged environment order:

1. local
2. dev
3. test
4. production

For each environment:

1. backup
2. inventory
3. dry-run
4. apply
5. re-run apply if idempotency needs to be proven
6. verify
7. start upgraded app
8. smoke test

### Backup and rollback

Always define rollback before running `apply`.

Minimum requirement:

- verified database backup taken immediately before migration
- exact currently deployed image tag or digest recorded before production migration

Preferred rollback model:

- before traffic restoration, rollback means restoring the database backup and redeploying the previously running image

### Batch sizing

Do not guess batch size in advance.

Start from real inventory data:

- row counts
- JSONB or payload sizes
- largest rows
- expected transaction cost

Then choose a conservative default and adjust only after dry-run or dev results.

## Code-level findings to reuse

### Avoid PostgreSQL `?` JSONB operator in prepared statements

Do not use SQL like:

```sql
snapshot ? 'someKey'
```

inside a prepared statement with bind parameters.

Reason:

- PostgreSQL treats `?` as a JSONB key-exists operator
- JDBC treats `?` as a bind placeholder

Use one of these instead:

- `jsonb_exists(snapshot, 'someKey')`
- or an escaped form only if absolutely necessary

For this codebase, `jsonb_exists(...)` is the clearer choice.

### Verification policy must be explicit

The next migration should decide in advance how to treat historical invalid rows, for example:

- `NULL` snapshots
- malformed payloads
- rows from abandoned legacy experiments

Recommended rule:

- verification should fail for rows that are in scope and expected to be valid
- verification may skip known out-of-scope invalid rows, but:
  - the skip policy must be explicit
  - skipped counts must be reported
  - the skipped shape must be documented

### Migration startup should not depend on fragile bean ordering

The migration runner should be able to initialize its own tracking metadata if startup ordering changes.

This was relevant because:

- migration mode starts the application in a special path
- bean initialization order can differ from normal web startup
- relying on a specific Flyway initializer bean name was not robust

### Verification should remain separate from apply

Keep these as separate concerns:

- `apply` mutates data
- `verify` confirms that the post-migration representation is valid

That separation helped uncover out-of-scope `NULL` rows without mixing them into write logic.

## Suggested design questions for the next migration

Before implementation, answer these explicitly:

1. What data moves where?
2. What is the authoritative source before migration?
3. What is the authoritative source after migration?
4. Can old and new representations coexist temporarily?
5. Does the migration require a write freeze?
6. What is the batch key?
7. What makes a row successfully migrated?
8. What makes a row successfully verified?
9. Which invalid historical rows are in scope, and which are intentionally out of scope?
10. What is the rollback point of no return?

## If participant groups are moved out of recruitment JSONB

This was mentioned as a likely future migration. If that becomes the next task, decide early:

- whether participant groups are copied first, then switched over later
- whether recruitment JSONB remains as a compatibility snapshot for one release
- whether reads must support both old and new layouts temporarily
- whether writes must dual-write during a transition window
- whether a backfill index is needed for lookup speed during migration

This matters because that migration is likely more structural than the Core 1.3 JSON rewrite.

## Suggested implementation shape for the next session

Unless there is a better reason to choose differently, start with:

1. inventory queries
2. Flyway schema changes for tracking and any new target tables
3. migration runner with `inventory`, `dry-run`, `apply`, `verify`
4. idempotent batch backfill
5. verification queries plus domain-level validation
6. runbook updates for Docker execution, backup, rollback, and smoke test

## Related files from this migration

Use these as references:

The carp.core 1.3 migration has been completed and its runners retired. The files below no longer
exist on `develop`; read them at the `v2.6.0` tag, e.g.
`git show v2.6.0:docs/core-1.3-data-migration.md`.

- `docs/core-1.3-data-migration.md`
- `src/main/kotlin/dk/cachet/carp/webservices/migration/Core13DataMigrationRunner.kt`
- `src/main/kotlin/dk/cachet/carp/webservices/migration/Core13SnapshotTransformer.kt`

## Recommended next-session starting point

At the start of the next migration session:

1. define the exact target schema change
2. answer the design questions in this file
3. decide whether the migration is online, write-frozen, or dual-write
4. add inventory queries before writing transformation code

That should prevent re-discovering the same operational and implementation issues.
