# CARP Core 1.3 data migration

This document is the single runbook for the CARP Core 1.3 data migration.

The migration is application-driven and non-web. It only rewrites `deployments.snapshot` and
`recruitments.snapshot`, but all PostgreSQL operations in this runbook assume PostgreSQL runs inside a Docker
container.

Run `apply` only while CARP Web Services 1.2 writers are stopped.

## What the migration changes

- Legacy deployment snapshots:
  - `isStopped=false` becomes `stoppedOn=null`
  - `isStopped=true` becomes `stoppedOn=updated_at`
  - `isStopped` is removed
- Legacy recruitment snapshots:
  - `_participantIds` becomes `_roleAssignments`
  - every migrated participant gets `AssignedTo.All`
  - participant-group representation defaults to an unnamed group
- Migration metadata is stored in:
  - `core_data_migration_runs`
  - `core_data_migration_failures`
  - `core_data_migration_rows`

## Preconditions

- The upgraded application build contains the reviewed Core 1.3 migration commits.
- PostgreSQL is running in Docker and you know:
  - container name
  - database name
  - database user
- You can run one-off `carp-ws` containers with the same:
  - `stack.env`
  - `PROFILE`
  - Docker network and PostgreSQL container used by the normal deployment
- You have enough free disk space for:
  - a full backup
  - WAL growth during JSONB rewrites
  - migration metadata tables

## Required application flags

Pass these arguments to every migration command:

```shell
--spring.main.web-application-type=none
--spring.task.scheduling.enabled=false
--spring.rabbitmq.listener.simple.auto-startup=false
```

Notes:

- `--spring.main.web-application-type=none` ensures the migration runs as a non-web process.
- `--spring.task.scheduling.enabled=false` avoids scheduled jobs during migration.
- `--spring.rabbitmq.listener.simple.auto-startup=false` avoids Rabbit listeners during migration.
- `resume` defaults to `true`. Set `--carp.core-1-3-migration.resume=false` to force a new run.
- In this repository, the recommended migration entrypoint is a one-off `docker compose run --rm --no-deps carp-ws`
  container with the migration arguments appended to the image entrypoint.

## 1. Backup

Create a backup inside the PostgreSQL container, then copy it out.

```shell
docker exec <pg-container> pg_dump -U <db-user> -d <db-name> -Fc -f /tmp/carp-pre-core13.dump
docker cp <pg-container>:/tmp/carp-pre-core13.dump ./carp-pre-core13.dump
```

Verify the dump is readable before continuing:

```shell
pg_restore -l ./carp-pre-core13.dump | head
```

Do not continue to `apply` until backup verification succeeds.

## 2. Inventory

Inventory records baseline counts and JSONB size percentiles without rewriting data.

```shell
docker compose --env-file stack.env --profile "${PROFILE}" run --rm --no-deps carp-ws \
  --spring.main.web-application-type=none \
  --spring.task.scheduling.enabled=false \
  --spring.rabbitmq.listener.simple.auto-startup=false \
  --carp.core-1-3-migration.mode=inventory
```

Inspect the latest run:

```sql
select id, mode, status, report
from core_data_migration_runs
order by id desc
limit 5;
```

## 3. Dry-run

Dry-run validates transformations and Core 1.3 deserialization without writing updated snapshots.

```shell
docker compose --env-file stack.env --profile "${PROFILE}" run --rm --no-deps carp-ws \
  --spring.main.web-application-type=none \
  --spring.task.scheduling.enabled=false \
  --spring.rabbitmq.listener.simple.auto-startup=false \
  --carp.core-1-3-migration.mode=dry-run \
  --carp.core-1-3-migration.batch-size=100
```

Review the result:

```sql
select id, mode, status, processed_count, migrated_count, failure_count
from core_data_migration_runs
order by id desc
limit 5;

select *
from core_data_migration_failures
order by created_at desc
limit 20;
```

Dry-run must finish with zero failures before `apply`.

## 4. Apply

Stop all CARP Web Services 1.2 writers before running `apply`.

```shell
docker compose --env-file stack.env --profile "${PROFILE}" run --rm --no-deps carp-ws \
  --spring.main.web-application-type=none \
  --spring.task.scheduling.enabled=false \
  --spring.rabbitmq.listener.simple.auto-startup=false \
  --carp.core-1-3-migration.mode=apply \
  --carp.core-1-3-migration.batch-size=100 \
  --carp.core-1-3-migration.rate-limit-ms=100
```

Monitor progress while it runs:

```sql
select id, status, last_deployment_id, last_recruitment_id, processed_count, migrated_count, failure_count
from core_data_migration_runs
order by id desc
limit 5;

select *
from core_data_migration_failures
order by created_at desc
limit 20;
```

If the process stops mid-run, rerun the same `apply` command. `resume=true` is the default and will continue from the
latest running checkpoint.

## 5. Idempotency check

Run `apply` a second time after the first successful `apply`.

```shell
docker compose --env-file stack.env --profile "${PROFILE}" run --rm --no-deps carp-ws \
  --spring.main.web-application-type=none \
  --spring.task.scheduling.enabled=false \
  --spring.rabbitmq.listener.simple.auto-startup=false \
  --carp.core-1-3-migration.mode=apply \
  --carp.core-1-3-migration.batch-size=100 \
  --carp.core-1-3-migration.rate-limit-ms=100
```

The accepted rerun should migrate zero additional legacy rows.

## 6. Verify

Verify checks two things:

- no legacy deployment or recruitment shapes remain
- every deployment and recruitment snapshot can be deserialized with Core 1.3

```shell
docker compose --env-file stack.env --profile "${PROFILE}" run --rm --no-deps carp-ws \
  --spring.main.web-application-type=none \
  --spring.task.scheduling.enabled=false \
  --spring.rabbitmq.listener.simple.auto-startup=false \
  --carp.core-1-3-migration.mode=verify \
  --carp.core-1-3-migration.batch-size=100
```

Also verify directly in PostgreSQL:

```sql
select count(*) as legacy_deployments
from deployments
where jsonb_exists(snapshot, 'isStopped');

select count(*) as legacy_recruitments
from recruitments
where jsonb_path_exists(snapshot, '$.participantGroups.*._participantIds');

select count(*) as failures
from core_data_migration_failures;
```

Expected results:

- `legacy_deployments = 0`
- `legacy_recruitments = 0`
- `failures = 0`

## 7. Start the upgraded application

Only after backup, dry-run, apply, idempotency check, and verify have all succeeded:

- start CAWS with the upgraded Core 1.3 build
- smoke test:
  - protocols
  - studies
  - recruitment
  - deployments
  - data streams

Important rollout constraint:

- the upgraded app must not be started in normal mode against an unmigrated database, because the Core 1.3 runtime
  expects migrated recruitment snapshots
- stop the normal `carp-ws` container before `apply`
- use one-off `docker compose run --rm --no-deps carp-ws ...` containers for migration modes
- start the normal `carp-ws` service again only after `verify` succeeds

## 8. Rollback

Rollback before restoring traffic means:

1. stop the upgraded application
2. restore the verified pre-migration backup
3. redeploy the pre-upgrade CAWS/Core 1.2 build

Restore example:

```shell
docker cp ./carp-pre-core13.dump <pg-container>:/tmp/carp-pre-core13.dump
docker exec <pg-container> dropdb -U <db-user> <db-name>
docker exec <pg-container> createdb -U <db-user> <db-name>
docker exec <pg-container> pg_restore -U <db-user> -d <db-name> --clean --if-exists /tmp/carp-pre-core13.dump
```

Do not restore traffic until the restored application version is back up and smoke tested.

## 9. Operational watch points

Watch these during `apply`:

- WAL growth
- disk free space on the Docker host
- long-running transactions
- lock contention
- failure growth in `core_data_migration_failures`
- outliers in `core_data_migration_rows` for duration and JSONB size

## 10. Batch size guidance

Start with `--carp.core-1-3-migration.batch-size=100`.

Why `100` is the recommended starting point:

- it matches the current migration default
- it keeps each transaction bounded when JSONB snapshots are large
- it reduces the risk of long lock hold times and large rollback segments
- it is a safer baseline for production-like data where row size may vary a lot

Tune from there based on measured results:

- stay at `100` if JSONB sizes are large or highly variable
- try `250` if dry-run and apply are stable and row sizes are moderate
- consider `500` only if:
  - dry-run and apply on dev or test show low lock pressure
  - WAL growth is acceptable
  - JSONB size percentiles are comfortably small
  - no slow outliers appear in `core_data_migration_rows`

Reduce below `100` if you observe:

- long-running transactions
- noticeable lock contention
- aggressive WAL growth
- memory pressure in the application container
- slow batches caused by very large snapshots

Do not pick the production batch size blindly. Measure it in this order:

1. run `inventory`
2. inspect JSONB size percentiles in `core_data_migration_runs.report`
3. run `dry-run` with `100`
4. test `apply` on dev or test with production-like data
5. increase only if metrics stay healthy

## 11. Acceptance criteria

All of the following must be true for an accepted production run:

- backup exists and was verified before `apply`
- dry-run finished with zero failures
- apply finished with zero failures
- running apply a second time migrated zero additional rows
- verify reported no legacy snapshots
- verify deserialized every deployment and recruitment snapshot with Core 1.3
- `core_data_migration_failures` is empty for the accepted run
- smoke tests passed after starting the upgraded app

## 12. Environment order

Run this sequence in order:

1. local database copy
2. dev
3. test
4. production
