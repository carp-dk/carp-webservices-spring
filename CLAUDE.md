# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CARP Web Services (CAWS) is a REST API that implements the [CARP Core Domain Model](https://github.com/cph-cachet/carp.core-kotlin) using Spring Boot (Kotlin). It enables researchers to run digital phenotyping / mHealth studies — managing study configuration, participant recruitment, and secure data collection.

## Build & Run Commands

```bash
# Build (includes tests)
./gradlew build

# Run all tests (test profile active automatically)
./gradlew test

# Run a single test class
./gradlew test --tests "dk.cachet.carp.webservices.ClassName"

# Run the application
./gradlew bootRun

# Static analysis (Detekt — auto-corrects)
./gradlew detekt

# Local Docker stack (PostgreSQL + Keycloak + RabbitMQ)
bash deployment.sh          # Start
bash deployment.sh -r       # Remove and rebuild
```

**Local development**: Run with profile `local` and configure an `.local.env` file (copy from `stack.env`). The IDE of choice is IntelliJ IDEA with the EnvFile plugin.

## Architecture

Three-layer **modular monolith** in Kotlin + Spring Boot:

```
REST Controllers  →  Service Layer  →  JPA Repositories  →  PostgreSQL
                         ↕
                  Spring Security (OAuth2/Keycloak)
```

Each feature domain lives under `src/main/kotlin/dk/cachet/carp/webservices/<domain>/`:
- `account`, `collection`, `consent`, `dashboard`, `dataPoint`, `datastream`, `deployment`, `document`, `email`, `export`, `file`, `protocol`, `security`, `statistics`, `study`
- `common/` — cross-cutting concerns (auth helpers, infrastructure utilities)

Within each domain, code is split by type: `controller/`, `service/`, `repository/`, `domain/`.

### CARP Core services vs. project-exclusive services

**CARP Core services** (`study`, `protocol`, `deployment`): Business logic lives entirely in the [carp.core-kotlin](https://github.com/cph-cachet/carp.core-kotlin) library. This repo supplies:
1. A single POST endpoint per subsystem that deserializes a core service-request DTO and delegates it.
2. JPA repository implementations that the core library uses for persistence.

**Project-exclusive services** (`dataPoint`, `datastream`, `file`, `collection`, `document`, `consent`, `export`, etc.): Fully implemented here with standard REST CRUD endpoints.

### Authorization

- Spring Security with Keycloak (OAuth2 resource server).
- Method-level security via `@PreAuthorize` with custom SpEL expressions (`canManageStudy()`, `isInDeployment()`, etc.) defined in `security/`.
- `CoreServiceContainer.kt` wires core services to their `*Authorizer` decorator wrappers.

## Serialization Strategy

Three frameworks coexist — choosing the wrong one causes subtle bugs:

| Context | Framework |
|---|---|
| CARP Core endpoints (service requests, Snapshot, StudyStatus, etc.) | `kotlinx.serialization` |
| REST API domains (DataStreams, Documents, Collections, Files, etc.) | Jackson |
| Database JSONB columns | Jackson |
| Legacy components | Java serialization |

Do **not** mix frameworks for the same object graph. When adding a new field to a core DTO, use `@SerialName`/`@Serializable`; for project-exclusive domain objects, use Jackson annotations.

## Persistence

- **Database**: PostgreSQL 15+ with HikariCP (max 20 connections).
- **Migrations**: Flyway — files in `src/main/resources/db/migration/V*.sql`. Migrations run automatically on startup.
- **ORM**: Hibernate 6 / Spring Data JPA. JSONB columns use `hypersistence-utils`.

When creating a new migration, increment the version prefix (`V7__...`) and never edit an already-applied migration.

## Testing

- JUnit 5 + SpringMockK (replaces Mockito). Test profile is set automatically.
- Tests live in `src/test/kotlin/dk/cachet/carp/webservices/`.
- Use `@MockkBean` (not `@MockBean`) for mocking in Spring context tests.

## Infrastructure Dependencies

Local stack (via `docker-compose.yml` / `deployment.sh`) provides:
- PostgreSQL — primary database
- Keycloak — OAuth2/OIDC identity provider (custom React theme in `keycloak-theme/`)
- RabbitMQ — async message queue (used by export and notification flows)

## Profiles

`local` · `development` · `testing` · `production` — configured in `src/main/resources/config/application.yml`.

## CI/CD

- **PRs**: `.github/workflows/pull-requests.yml` — runs `./gradlew build`.
- **Docker push**: `.github/workflows/create-docker.yml` — builds and pushes to `registry.carp.dk` on push to `master` (production), `testing`, or `develop` (development) branches.
