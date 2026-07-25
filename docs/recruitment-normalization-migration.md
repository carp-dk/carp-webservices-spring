# Recruitment participant-group normalization — migration runbook

How to run the migration that moves the `participants` and `participantGroups` maps out of
`recruitments.snapshot` into the normalized tables, and how to cut the application over to them.

For the design and rationale see [participant-group-normalization.md](participant-group-normalization.md).
For shared operational guidance (backup, rollback model, staged environment order, batch sizing) see
[core-1.3-data-migration.md](core-1.3-data-migration.md) and
[next-db-migration-notes.md](next-db-migration-notes.md) — this runbook does not repeat them.

## Pieces involved

- **Runner** — `RecruitmentNormalizationRunner`, gated by `carp.recruitment-normalization.mode`, run as a
  non-web process (like the Core 1.3 runner). Reuses the `core_data_migration_*` tracking tables with
  `migration_name = 'recruitment-participant-normalization'`.
- **Application flag** — `carp.recruitment.normalized-store-enabled` (env var
  `CARP_RECRUITMENT_NORMALIZED_STORE_ENABLED`), default `false`. When true the app reads/writes the
  normalized tables instead of the blob. It lives in `stack.env` so it is durable per environment.
- **Flyway V8** — creates the three normalized tables; applied automatically on the first startup of the
  new image (web or migration mode). Additive, leaves `recruitments` untouched.

## Modes

| Mode | Effect |
|---|---|
| `inventory` | Counts recruitments + NULL snapshots into the run report. Read-only. |
| `dry-run` | Decodes + decomposes every recruitment (proves decodability); writes nothing. |
| `apply` | Backfills the normalized tables from the blobs (diff-based; idempotent). |
| `verify` | Reconstructs each recruitment from the tables and confirms it matches the blob object. |
| `strip` | Empties the two maps from each blob (keeps the envelope), after re-verifying. Cutover cleanup. |

Options (all `carp.recruitment-normalization.*`): `batch-size` (default 25), `rate-limit-ms` (default 0),
`resume` (default true — set `false` to force a fresh run per mode).

## Prerequisites

1. **Core 1.3 first.** On any environment not yet on carp.core 1.3, run the
   [Core 1.3 migration](core-1.3-data-migration.md) `apply` + `verify` **before** this one. Legacy
   1.2-shape recruitments fail to decode and would be skipped (never normalized).
2. **Backup** the database and record the currently deployed image tag (see the 1.3 runbook).

## Running it (production / Docker)

Same shape as the 1.3 migration — a one-off non-web container per mode. `${PROFILE}` comes from `stack.env`.

```shell
docker compose --env-file stack.env --profile "${PROFILE}" run --rm --no-deps carp-ws \
  --spring.main.web-application-type=none \
  --spring.task.scheduling.enabled=false \
  --spring.rabbitmq.listener.simple.auto-startup=false \
  --spring.autoconfigure.exclude=com.c4_soft.springaddons.security.oidc.starter.reactive.client.ReactiveSpringAddonsOAuth2AuthorizedClientBeans,com.c4_soft.springaddons.security.oidc.starter.reactive.client.ReactiveSpringAddonsOidcClientWithLoginBeans,com.c4_soft.springaddons.security.oidc.starter.reactive.resourceserver.ReactiveSpringAddonsOidcResourceServerBeans \
  --carp.recruitment-normalization.mode=inventory
```

Run in order, changing only the `mode`: `inventory` → `dry-run` → `apply` → `verify`. Add
`--carp.recruitment-normalization.batch-size=25` (or lower for very large recruitments) and
`--carp.recruitment-normalization.resume=false` to force a fresh run.

## Running it (local fallback)

Local terminal outside Docker needs the profile + a few extra flags (identical to the 1.3 runbook,
only the migration properties differ). Load `stack.env`/`.local.env` first (`set -a; source .local.env; set +a`).

```shell
./gradlew bootRun --args='--spring.main.web-application-type=none --spring.profiles.active=local --spring.task.scheduling.enabled=false --spring.rabbitmq.listener.simple.auto-startup=false --spring.autoconfigure.exclude=com.c4_soft.springaddons.security.oidc.starter.reactive.client.ReactiveSpringAddonsOAuth2AuthorizedClientBeans,com.c4_soft.springaddons.security.oidc.starter.reactive.client.ReactiveSpringAddonsOidcClientWithLoginBeans,com.c4_soft.springaddons.security.oidc.starter.reactive.resourceserver.ReactiveSpringAddonsOidcResourceServerBeans --carp.recruitment-normalization.mode=apply --carp.recruitment-normalization.resume=false'
```

## Checking results

```sql
SELECT mode, status, processed_count, migrated_count, failure_count, report
FROM core_data_migration_runs
WHERE migration_name = 'recruitment-participant-normalization' ORDER BY id;

SELECT r.outcome, count(*)
FROM core_data_migration_rows r
JOIN core_data_migration_runs x ON x.id = r.run_id
WHERE x.migration_name = 'recruitment-participant-normalization'
GROUP BY r.outcome;
```

Expected: `apply` → most rows `MIGRATED`, a few `SKIPPED`, `failure_count = 0`, status `COMPLETED`.
`verify` → `VALIDATED` + the same `SKIPPED`. A non-zero `failure_count` blocks completion — investigate
before proceeding.

**Skip policy.** NULL snapshots and recruitments that fail `WS_JSON` decode (e.g. legacy stale-`type`
discriminator rows) are recorded as `SKIPPED`, not failures — they are never migrated or stripped, and
their blob is left intact. Decide per environment whether any such rows need fixing before cutover
(with the flag on they read as empty rather than throwing).

## Cutover (flip the flag)

After `apply` + `verify` are clean:

1. Set `CARP_RECRUITMENT_NORMALIZED_STORE_ENABLED=true` in that environment's `stack.env`.
2. Restart the `carp-ws` app (the flag is read once at startup). From now on the app reads/writes the
   normalized tables; new and updated recruitments land there automatically, and touched recruitments'
   blobs become envelope-only on their next write.
3. Smoke test. **Rollback up to this point is clean**: set the flag back to `false` and restart — the
   blob is still full.

## Strip (housekeeping, deferrable)

Once the table-backed reads are trusted in production, reclaim storage on the recruitments that were
never touched after the flip:

```shell
# same command as above, with:
  --carp.recruitment-normalization.mode=strip
```

`strip` re-verifies each recruitment against the tables, then empties its two maps (envelope kept
byte-exact). It skips undecodable/NULL rows and refuses to strip anything the tables cannot reproduce.

**Point of no return:** after `strip`, the flag can no longer be flipped back to `false` for those
recruitments (the blob maps are gone → they would read empty). Roll back only via database restore.

## Rollout order

Per environment, in order: **local → dev → test → production**. Each: backup → (Core 1.3 if needed) →
inventory → dry-run → apply → verify → flip flag → smoke test → (later) strip.
