# Participant-group normalization — design & migration plan

**Status:** draft for review · **Author:** (design session) · **Target carp.core:** 1.3.0

## 1. Goal

The recruitment participant data is stored as an opaque carp.core snapshot blob in
`recruitments.snapshot`. Two maps inside that blob — `participants` and the staged
`participantGroups` — are the source of most query pain and are hard to evolve. This
plan normalizes **those two maps only** into relational tables that we own, while
keeping the rest of the snapshot as a residual blob.

Driver: **data-model / schema restructuring** (not just perf), plus the query cost
and full-blob-rewrite writes that the JSONB model forces.

## 2. Scope

**In scope** (inside `RecruitmentSnapshot`):
- `participants: Set<Participant>`
- `participantGroups: Map<UUID, StagedParticipantGroup>` (staged groups + role assignments)

**Out of scope** (stay as JSONB, unchanged):
- `RecruitmentSnapshot.studyProtocol`, `invitation` — large, never queried → residual blob
- `deployments.snapshot` (`StudyDeploymentSnapshot`) — separate, much larger project
- `participant_groups` table (`ParticipantGroupSnapshot`, deployments domain) — *not* the
  target; candidate for indexes only, decided later
- carp.core 1.3.1 bump / `stopParticipantGroup` workaround — deliberately decoupled; not
  gating this work

## 3. The binding constraint

carp.core **owns** the `Recruitment` aggregate. We implement its `ParticipantRepository`
interface:

```
addRecruitment(recruitment)          getRecruitment(studyId): Recruitment?
updateRecruitment(recruitment)       getRecruitmentWithParticipantGroup(groupId): Recruitment?
removeStudy(studyId): Boolean
```

Every read/write is a **whole-aggregate** operation. Normalizing therefore means:
- **Read:** rebuild an identical `RecruitmentSnapshot` from our tables + residual blob, then
  `Recruitment.fromSnapshot(...)`.
- **Write:** decompose the incoming aggregate's snapshot into row upserts/deletes (diff against
  current state — core gives us no delta).

**Fidelity is the entire safety property:** `reconstruct(decompose(snapshot))` must be
serialization-equivalent under carp.core's own `WS_JSON`, for every core version we run.
This is a permanent tax: each future carp.core bump must re-pass the fidelity suite.

### 3a. `updateRecruitment` — needs attention (candidate upstream carp.core issue)

`ParticipantRepository.updateRecruitment(recruitment)` hands us the **entire** `Recruitment`
aggregate on *any* change — add one participant → we receive all participants + all groups. So
even with normalized child tables, cheap incremental writes require us to **diff** the incoming
aggregate against the DB and apply only the delta; a naive implementation would re-write every
child row on every change (better than today's whole-blob rewrite, but still O(all participants)).

The `Recruitment` aggregate **already knows the delta**: it emits domain events —
`Event.ParticipantAdded`, `ParticipantGroupAdded`, `ParticipantGroupUpdated` (see `Recruitment.kt`)
— which are **designed to drive persistence** (event-sourced writes). That wiring was simply never
implemented; the current SPI discards the events and exposes only the whole snapshot. Consuming
those events to apply targeted DB writes is the intended long-term mechanism and would remove the
diff entirely.

**Not for today.** The event-driven persistence is the right future direction (implement here, or
contribute the delta-aware SPI upstream), but this project ships with **diff-on-our-side** writes.
Orthogonal to the schema choice — applies to any normalized design.

## 4. Verified on-disk shape (carp.core 1.3.0)

Confirmed against carp.core v1.3.0 source **and** this repo's `Core13SnapshotTransformer`
+ `validateRecruitment` (which round-trips these blobs through `WS_JSON`). Note: kotlinx
serializes class-body properties with **backing fields**, not only constructor properties —
so `_roleAssignments` and `isDeployed` are serialized; the getters `roleAssignments` /
`participantIds` are not.

```json
{
  "id": "<uuid>", "createdOn": "<instant>", "version": <int>, "studyId": "<uuid>",
  "studyProtocol": <StudyProtocolSnapshot|null>, "invitation": <StudyInvitation|null>,
  "participants": [
    { "accountIdentity": { "__type": "…EmailAccountIdentity", "emailAddress": "…" },
      "id": "<uuid>" }
  ],
  "participantGroups": {
    "<groupId>": {
      "id": "<uuid>",
      "representation": { "name": <string|null> },
      "_roleAssignments": [
        { "participantId": "<uuid>",
          "assignedRoles": { "__type": "…AssignedTo.All" } }
      ],
      "isDeployed": <bool>
    }
  }
}
```

Leaf types (all `@Serializable`, confirmed):
- `Participant` = `{ accountIdentity, id }`; `accountIdentity` polymorphic:
  `EmailAccountIdentity{emailAddress}` | `UsernameAccountIdentity{username}` (discriminator `__type`).
- `StagedParticipantGroup` = `id`, `representation{name:String?}`, `_roleAssignments`, `isDeployed`.
- `AssignedParticipantRoles` = `{ participantId, assignedRoles }`; `assignedRoles` =
  `AssignedTo.All` | `AssignedTo.Roles{roleNames:Set<String>}`.

⚠️ **Latent test gap in core:** `StagedParticipantGroup` is a `data class` whose `equals()`
covers only `id`/`representation`; core's `SnapshotTest` compares by equality, so it cannot
detect if `_roleAssignments`/`isDeployed` ever stop round-tripping. Our fidelity suite must
compare **serialized JSON**, not objects.

## 5. Target schema (next migration = V8)

**Approach — full normalization of the two growing maps, envelope stays a blob.** The two maps
that grow unbounded (`participants`, `participantGroups`) become fully typed child tables; the
recruitment envelope — including the genuinely complex `studyProtocol` — stays as the core
snapshot, stored with those two maps **emptied out**. Participant/group writes then never touch
the recruitment row, and reads assemble the full snapshot from the child tables.

Why full columns (not per-entity JSON fragments): the target types are simple and stable — the
only polymorphism is `AccountIdentity` (2 cases) and `AssignedTo` (`All` | `Roles{roleNames}`).
Full columns buy FK integrity, constraints, ad-hoc SQL/analytics, and schema evolution by
migration. The complex nested graph (`studyProtocol`) is *not* normalized — it rides in the
envelope blob.

```
recruitments                              (existing table, evolved)
  id                                        -- existing surrogate PK
  study_id UUID
  snapshot  JSONB                           -- core RecruitmentSnapshot with participants:[] and
                                            --   participantGroups:{} EMPTY. Holds envelope
                                            --   (id, version, createdOn, studyId) + studyProtocol
                                            --   + invitation. PERMANENT residual envelope;
                                            --   reuses core serializer. version → optimistic lock.

recruitment_participants                   (Auditable)
  id                                        -- surrogate PK (repo convention)
  recruitment_id, study_id
  participant_id UUID                       -- core Participant.id; UNIQUE(study_id, participant_id)
  account_identity_type TEXT                -- 'email' | 'username'
  username TEXT, email_address TEXT         -- pg_trgm GIN index (ILIKE '%x%')
  sort_order INT                            -- deterministic pagination only (Set<> is unordered)
  created_at / updated_at

recruitment_participant_groups             (Auditable)
  id                                        -- surrogate PK
  recruitment_id, study_id
  group_id UUID                             -- core group id == study_deployment_id once deployed
  is_deployed BOOLEAN NOT NULL
  name TEXT NULL                            -- representation.name
  created_at / updated_at

recruitment_participant_group_members
  group_id UUID        -> recruitment_participant_groups
  participant_id UUID  -> recruitment_participants
  assigned_all BOOLEAN NOT NULL             -- AssignedTo.All vs Roles
  role_names TEXT[] NULL                    -- AssignedTo.Roles.roleNames when not assigned_all
  PK(group_id, participant_id)
```

Notes:
- **No `participant_snapshot`** — `Participant` is only `{accountIdentity, id}`, fully captured by
  the columns.
- **The members table carries the roles** (`assigned_all`, `role_names[]`) — this is the delta from
  the earlier hand-drawn schema, which kept roles inside a `group_snapshot`. Full normalization
  moves them into columns.
- **No per-entity JSON fragments at all.** The group data is fully typed; the fidelity harness
  (shadow-verify) is the safety net instead of a transitional blob.
- `accountIdentity` reconstructs from `account_identity_type` + `username`/`email_address`.

**Concrete DDL shipped in `V8__normalize_recruitment_participants_and_groups.sql`.** Finalized decisions
beyond the sketch above:
- **Additive only.** `recruitments` is left unchanged; `snapshot` stays authoritative for the whole
  transition. `study_id`/`version` are NOT promoted to `recruitments` columns yet — deferred to the
  write-model phase when optimistic locking lands.
- Child tables carry `recruitment_id INTEGER REFERENCES recruitments(id) ON DELETE CASCADE` **and** a
  **denormalized `study_id`**, so participant/group queries never join back to `recruitments`.
- `recruitment_participants` / `recruitment_participant_groups` use a `BIGSERIAL` surrogate id (repo
  convention; BIGINT because participants grow unbounded) and extend `Auditable`
  (`timestamp without time zone` audit columns, matching existing tables).
- `recruitment_participant_group_members` is keyed `(group_id, participant_id)`, FKs to
  `recruitment_participant_groups(group_id)` (its UNIQUE natural key), and holds the role assignment
  (`assigned_all`, `role_names TEXT[]`).
- Search indexes: `pg_trgm` GIN with `gin_trgm_ops` on `username` and `email_address`.
- **DB-enforced integrity** (the reason we chose full normalization over JSON fragments):
  - `recruitment_participants` CHECK: `account_identity_type` is exactly `'email'`/`'username'`, the
    matching column is non-null and the other is null — rejects unknown types and contradictory rows.
  - `recruitment_participant_group_members` carries `study_id` and FKs
    `(study_id, participant_id)` → `recruitment_participants(study_id, participant_id)`, so a member
    must reference a real participant in the same study.
  - `recruitment_participant_group_members` CHECK: `assigned_all` ⟺ `role_names IS NULL`
    (`AssignedTo.All` has no roles; `AssignedTo.Roles` carries them).
- **NULL snapshots** are processed and recorded as `SKIPPED` rows (not silently excluded from the
  batch), so every in-scope recruitment is accounted for in `core_data_migration_rows`.

> Not yet validated against a live Postgres (no local DB/Docker this session) — first real run is at
> Flyway startup / in the Phase-2 Postgres test.

## 6. Query wins this unlocks

- **Participant search** → indexed `email`/`username` columns, replacing the
  `jsonb_array_elements(snapshot->'participants')` fan-out + per-row Jackson parse in
  `RecruitmentRepositoryImpl`.
- **`getRecruitmentWithParticipantGroup` / `findRecruitmentByParticipantGroupId`** → PK lookup
  on `staged_participant_groups`, replacing the `@>` GIN containment scan.
- **Single participant/group change** → one-row write instead of rewriting the entire
  `recruitments` blob.

## 7. Phasing (risk-ordered, within recruitment)

Phase by blast radius, not by table.

1. **Fidelity harness (zero blast radius).** Build decompose/reconstruct + a round-trip suite
   that asserts serialized-JSON equality against real snapshot fixtures. **No schema change,
   no read/write change, blob stays authoritative.** If fidelity can't hit ~100%, we learn it
   here for near-zero cost.
2. **Read model.** Land V8 tables + backfill; rewrite search/count/status read paths onto
   relational queries. Blob still source of truth for writes.
3. **Write model.** Flip writes to diff-decompose (dual-write blob + tables), then flip source
   of truth, then a later migration drops the blob column.

## 8. Backfill

Reuse the existing app-level migration framework (`Core13DataMigrationRunner` +
`core_data_migration_runs/failures/rows`): a runner that reads each `recruitments` blob,
deserializes with `WS_JSON`, writes normalized rows, records per-row success/failure,
idempotent and re-runnable. Not raw SQL — same kotlinx path as runtime, so fidelity matches.
Precondition: confirm the Core13 migration has fully applied everywhere (no rows matching
`jsonb_exists(snapshot,'isStopped')` / legacy `_participantIds`).

**Skip policy (decided against real data, 2026-07-24).** Of 136 recruitments in a prod-like DB:
- **131 decode + round-trip losslessly** (validated by `RecruitmentNormalizerRealDataTest`, incl. a
  2.5 MB / ~13k-participant blob and all-deployed groups).
- **2 have NULL snapshots** — skip + report (out of scope).
- **3 (ids 411/422/429) fail `WS_JSON` decode** — their `assignedRoles` use the *old* class
  discriminator `"type"` instead of `"__type"` (leftover data from an already-fixed bug; these also
  fail to decode in the running app today). **Accepted policy: skip + report, do not fix.** The
  runner records decode failures in `core_data_migration_failures`; `verify` treats undecodable /
  NULL rows as known out-of-scope and reports the skipped count rather than failing.

**Batch sizing:** a single recruitment can decompose into ~13k child rows, so batch by a small number
of recruitments per transaction, not by a fixed child-row count.

## 9. Rollout & verification (as built)

> Step-by-step commands lived in `docs/recruitment-normalization-migration.md`, retired along with
> `RecruitmentNormalizationRunner` once the migration completed. Read them at the `v2.6.0` tag:
> `git show v2.6.0:docs/recruitment-normalization-migration.md`.

Everything is gated by `carp.recruitment.normalized-store-enabled` (default **off**). With the flag off
the app reads and writes the blob unchanged, so all of the below ships dormant and safe. Because the
migration runs in an **offline maintenance window** (no concurrent traffic), no dual-write / shadow-read
period is needed.

Per environment (dev is already on carp.core 1.3; prod needs the Core13 migration first):

1. **Backfill** — run the normalization runner: `inventory` → `dry-run` → `apply` → `verify`
   (non-web, `carp.recruitment-normalization.mode=…`). Reuses the `core_data_migration_*` tracking tables.
2. **Flip the flag on.** From here `getRecruitment` reconstructs from the tables, and every write
   decomposes into them and persists an **envelope-only** blob. New data and any touched recruitment
   land in the tables automatically — this is the actual win.
3. **Smoke test.** The blob is still full for untouched recruitments, so flipping the flag back off is a
   clean rollback up to this point.
4. **`strip` (housekeeping, deferrable).** Empties the two maps from dormant recruitments' blobs after
   confirming the tables reconstruct them. Not required for correctness (reads ignore the blob maps when
   the flag is on) — it only reclaims storage. **Point of no return:** once stripped, the flag can't be
   flipped back off. Run it in a later window, once the table-backed reads are trusted in prod.

Skipped rows (undecodable / NULL — see §8) are never stripped, and `strip` refuses to touch any
recruitment the tables can't reconstruct.

## 10. Risks

- **Fidelity drift** on every future carp.core version (the accepted tax) — gated by the suite.
- **Diff-write concurrency** — optimistic locking on `version`.
- **Backfill lock/time** on large `recruitments`.
- **Two sources of truth** during transition — mitigated by shadow-verify + dual-write.

## 11. Resolved decisions

- **Deployed-group id:** `StagedParticipantGroup.id` **is** the study-deployment id once deployed
  (per core: "used as deployment ID once the participant group is deployed"). So `group_id`
  already carries it — **no separate `study_deployment_id` column**; `is_deployed` + `group_id`
  is sufficient.
- **Search index = `pg_trgm` GIN** on `email` / `username`. The current query is
  `ILIKE CONCAT('%', :search, '%')` — a leading-wildcard *contains* match, which **btree cannot
  accelerate** (btree only helps `=`, prefix `LIKE 'x%'`, and ordering). `pg_trgm` GIN is exactly
  the index for case-insensitive substring search. Requires `CREATE EXTENSION pg_trgm` (Flyway
  step). Caveat: 1–2 char search terms fall back to scan (trigrams are 3-char); acceptable.
- **`participant_groups` (deployments domain): leave entirely alone** — already one row per group
  and fast; not touched by this work.

## 12. Open questions

- The search also does `id ILIKE '%x%'` (substring of a UUID). If we keep id-search, it needs a
  trigram index on `participant_id::text` too — or reconsider whether substring-on-UUID search is
  actually useful. Decide during Phase 2.
- Compare this design against the author's own earlier schema design before finalizing.
