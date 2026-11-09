# Admin Section

Endpoints for system administrators (`SYSTEM_ADMIN` realm role, checked with `isAdmin()`), plus one public
endpoint that reads the system announcement. The existing `/api/internal/statistics` endpoints are unchanged.

## 1. Server Info

- `GET /api/admin/server-info` - what this instance is running and whether its dependencies are healthy.

```json
{
  "version": "2.7.0",
  "environment": "production",
  "hostname": "3f2c1a9b7d4e",
  "startedAt": "2026-11-01T08:00:00Z",
  "uptimeSeconds": 86400,
  "jvm": { "javaVersion": "21.0.5+11", "availableProcessors": 8, "heapUsedBytes": 512000000, "heapMaxBytes": 4294967296 },
  "health": {
    "status": "UP",
    "components": { "db": "UP", "rabbit": "UP", "mail": "UP", "diskSpace": "UP", "ping": "UP", "keycloak": "UP" }
  }
}
```

- `version` is `spring.application.version` (the same value as `/actuator/info`).
- `health.components` are the actuator health contributors (status only, no details) plus `keycloak`, a
  2-second unauthenticated probe of the realm's public metadata. `health.status` is the actuator's own
  aggregate combined with `keycloak`, by severity: `DOWN` outranks `OUT_OF_SERVICE`, which outranks `UP`;
  `UNKNOWN` components don't lower it.

## 2. Studies

- `GET /api/admin/studies[?live=true|false]` - every study on the instance, newest first.

```json
[
  {
    "studyId": "…", "name": "Sleep study", "description": "…", "isLive": true, "applicationName": "carp-studies-app",
    "owner": { "id": "…", "email": "researcher@example.com", "fullName": "Jane Doe" },
    "createdAt": "2026-03-01T10:00:00Z", "updatedAt": "2026-03-04T09:12:00Z",
    "firstDeploymentAt": "2026-03-05T12:00:00Z",
    "participantCount": 120, "deploymentCount": 118, "fileCount": 12
  }
]
```

- **There is no go-live timestamp** stored anywhere. `firstDeploymentAt` (first participant group deployed) is
  the closest proxy for when a study started; `null` if nothing has been deployed.
- `updatedAt` moves on every save of the study (rename, invitation, protocol, go-live).
- `owner.email`/`fullName` come from Keycloak: one listing of the researcher role, plus individual lookups
  for owners who aren't researchers. They are `null` if the account no longer exists.

- The listing runs with a 15-second statement timeout. An instance too large to list in time gets a `500`
  saying the query was cancelled, rather than holding a database connection.

### Data uploaded per study

Uploaded data is not part of the list: it touches every data stream of a study, which is too much to do for
every study on each listing at scale. It is a study endpoint rather than an admin one, so the study's own
researchers and research assistants can use it too (`canManageStudy` or `canLimitedManageStudy`; system
admins pass both):

- `GET /api/studies/{study-id}/data-uploads`

```json
{ "studyId": "…", "dataStreamCount": 944, "measurementCount": 1830211, "lastDataUploadAt": "2026-11-05T22:41:07Z" }
```

- `404` if the study does not exist (for admins; anyone else gets `403` first).
- Figures cover the study's deployed groups. Per data stream only its highest sequence is read (one
  index lookup each, not a scan of all uploaded data), so:
  - `measurementCount` = sum over streams of (highest sequence id + 1). Exact as long as clients upload
    sequence ids contiguously from 0, which they do.
  - `lastDataUploadAt` = latest `updated_at` of those highest-sequence rows.
- Legacy data points are not counted.
- Runs with a 15-second statement timeout; a study too large to answer in time gets a `500` saying the
  query was cancelled.

## 3. Researchers

- `GET /api/admin/researchers` - every account with the `researcher` realm role, newest account first.

```json
[
  {
    "id": "…", "username": "researcher@example.com", "email": "researcher@example.com", "fullName": "Jane Doe",
    "enabled": true, "emailVerified": true, "createdAt": "2025-02-10T08:30:00Z",
    "ownedStudyCount": 4, "liveStudyCount": 2
  }
]
```

- Account details come from Keycloak (the role's members, fetched 100 at a time). Only direct role mappings
  are listed, which is how CAWS grants the researcher role.
- **Requires `realm-management` → `view-realm`** on the CAWS service account (the `KC_CLIENT_ID` client),
  in addition to the user-management roles it already has. Keycloak checks it when listing a role's
  members; without it this endpoint returns `500` (Keycloak answers `403`).
- `ownedStudyCount`/`liveStudyCount` count studies whose owner is this account. Studies the researcher only
  manages through a claim (as a co-researcher) are not counted.
- **No last-active time** is shown: Keycloak 25 does not record one and login events are off in the realm.

## 4. System Announcement

A single message shown to all users, e.g. a maintenance notice. One at a time, no schedule: it stays until
an admin changes or clears it.

- `PUT /api/admin/announcement` (admin) - set or replace it. Body:
  `{ "message": "Maintenance tonight 20:00-22:00 CET.", "severity": "WARNING" }`
  - `severity`: `INFO` (default), `WARNING`, or `CRITICAL`.
  - `message`: at most 1000 characters; leading/trailing whitespace is trimmed.
  - Returns `200` with `{ message, severity, updatedAt, updatedBy }`.
  - **An empty, blank or missing `message` clears the announcement** and returns `204`. Clearing when nothing
    is set is also `204`.
- `GET /api/announcement` (**public, no authentication**) - returns `200` with
  `{ message, severity, updatedAt }`, or `204` if none is set. Responses carry `Cache-Control: max-age=60`,
  so a change can take up to a minute to reach clients. Any `Authorization` header is ignored here, so a
  client that sends an expired token still gets the announcement instead of a `401`.
