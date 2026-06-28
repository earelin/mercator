# CLAUDE.md

Guidance for Claude Code working in this repository.

## Read first

`docs/` is the authoritative design — read it before writing code. It defines what to build and the constraints that govern it.

- `docs/specs/` — **what** the system does.
- `docs/features/` — **how** each spec is implemented, plus the issue backlog.
- `docs/architecture/` — **why** (ADRs). Start at [`docs/architecture/README.md`](docs/architecture/README.md): it indexes the ADRs and carries a current-state architecture overview — the recommended entry point.
- [`docs/CLAUDE.md`](docs/CLAUDE.md) — doc organisation, authoring conventions, ADR lifecycle.

## What Mercator is

Turns Spain's official mercantile gazette — the **BORME** (*Boletín Oficial del Registro Mercantil*) — into a queryable database and graph of companies, their associated people (administrators, attorneys…), and the links between them (shared administrators, shared address, multi-hop). Primary consumer is a sibling **public-contracts** project detecting related bidders/awardees. Mandate: **cheap and simple** — a single EU VPS, free public data, no commercial BORME API.

**Status: early implementation.** The fetch/parse/cache/persistence layers and the Flyway baseline (`V1.0.0__baseline.sql`) exist. The ingestion service, resolution functions, read API, and historical-import engine are still to be built (the import endpoint is scaffolded).

## Commands

```bash
./gradlew run            # start the server (needs `docker compose up -d` first)
./gradlew check          # unit tests + Checkstyle + PMD + CPD + Error Prone/NullAway + SpotBugs/FindSecBugs (no Docker)
./gradlew test           # fast unit tests only
./gradlew integration    # integration tests — controllers + adapters (needs Docker; not in `check`)
./gradlew acceptance     # black-box tests over Docker Compose (needs Docker; not in `check`/`build`)
./gradlew build          # compile + test
```

## Verifying changes — run `scripts/ci.sh`

`scripts/ci.sh` is the local CI pipeline (also a git pre-push hook). It accepts named checks: `./scripts/ci.sh <check>...`. **Run only the checks matching what you changed** — full `./scripts/ci.sh` is the catch-all.

| You changed… | Run |
|---|---|
| `*.md` (any Markdown) | `markdown links mermaid` |
| Docker Compose files (`docker-compose.yml`, `compose/*`) | `compose` |
| Java in `src/main` | `check build opengrep` |
| Java in `src/test` / `src/integration` / `src/acceptance` | `check build` |
| `src/main/resources/db/migration/*.sql` (Flyway) | `sql` |
| `docs/specs/api.openapi.yaml` | `openapi` |
| `.github/workflows/*` | `actions` |

Notes: `links` hits the network — skip external with `CHECK_EXTERNAL=0`. `opengrep` (SAST) and SpotBugs/NullAway scope to `src/main` only — test fixtures legitimately do "unsafe" things. Tooling required: markdownlint-cli2, lychee, mmdc (+ system Chrome), npx, sqlfluff, opengrep, actionlint. `./scripts/ci.sh --help` lists every check.

`scripts/api-conformance.sh` (kept out of `ci.sh`) drives a **running** server with property-based cases from the OpenAPI doc (`schemathesis`), asserting response conformance + admin-auth enforcement. Needs `./gradlew run` first; configure via `MERCATOR_BASE_URL`, `MERCATOR_API_KEY`, `SCHEMATHESIS_MAX_EXAMPLES`.

## Repository layout

Single-project **Gradle 9.5** build (pinned via the wrapper) producing one **Java 25 + Micronaut** server artifact. Layers are separated by **package**, not by Gradle module:

- `net.earelin.mercator.domain.*` — domain/application core: model, ports, ingestion/normalisation/parsing. Imports no other layer. A domain object **may** carry `@MappedEntity`/`@Serdeable` and serve as the DB entity / API body directly — a separate DTO only where the shape genuinely differs (ADR-0014).
- `net.earelin.mercator.infrastructure.*` — driven adapters (BOE HTTP client, document cache, extractors, JDBC persistence). May use infrastructure-facing Micronaut tooling (config binding, declarative HTTP client, caching/retry) — never driving-side concerns.
- `net.earelin.mercator.application.*` — driving adapters + wiring: the **read-only** API and gated import endpoint (`application.rest`; admin-only under `application.rest.admin`, e.g. `application.rest.admin.imports`), the daily `@Scheduled` job, and `@Factory` composition beans.

Only the dependency *direction* is enforced (domain ← infrastructure ← application).

## Code style

**Comment sparingly.** Let clear names and small methods carry intent. Comment only a non-obvious *why* (rationale, workaround, spec/ADR reference, subtle invariant) — never narrate *what* the code already says, and no section banners, changelog notes, or TODOs. Prefer deleting a stale comment over updating it. Match the surrounding density; when in doubt, fewer.

## Testing conventions

- **Prefer stubs over mocks.** Assert on observable outcomes / captured state, not interactions via `verify()`. For stateful collaborators use a small hand-written stub that captures state; reserve Mockito for **stubbing** (`when(…).thenReturn(…)`) stateless inputs.
- **AssertJ** (`assertThat`) for assertions; **assertj-db** for database checks. Test method names are **snake_case**.
- **Three source sets** (JVM Test Suite plugin):
  - `src/test` — fast **unit** tests (domain core + pure-logic/in-memory infrastructure). No Docker; run by `test`/`check`.
  - `src/integration` — tests crossing a **process boundary** only: controllers over Micronaut's embedded server (**REST Assured**), JDBC adapters against real Postgres (Testcontainers), HTTP transport over a socket. Needs Docker; **not** in `check`.
  - `src/acceptance` — **black-box** tests: build the production image (`dockerBuild`), stand up the full stack (app, Postgres, **WireMock** for BORME/BOE) via Testcontainers' `ComposeContainer`, drive the REST API over the network (**REST Assured**). Never touches production classes — isolated from the Micronaut BOM, declaring its own catalog versions. In neither `check` nor `build`.
- `check` runs **Checkstyle** (`config/checkstyle/`), **PMD** (`config/pmd/`), **CPD**, **Error Prone**, **NullAway**, and **SpotBugs + Find Security Bugs** — keep all green. NullAway and SpotBugs run on `src/main` only; the `net.earelin.mercator` packages are `@NonNull` by default, so annotate genuinely-nullable components/params/returns with Micronaut's `io.micronaut.core.annotation.@Nullable`. SpotBugs excludes live in `config/spotbugs/exclude.xml` — prefer fixing a finding over widening the filter; keep each entry commented.

## Tech stack (per the ADRs)

See [`docs/architecture/README.md`](docs/architecture/README.md) for full rationale.

- **PostgreSQL 18**, single datastore (`pg_trgm`, `fuzzystrmatch`, `unaccent`). ADR-0004.
- **Micronaut Data JDBC** (compile-time repos, HikariCP) — `@JdbcRepository` interfaces in `infrastructure.persistence` over `@MappedEntity` domain objects, custom `@Query` SQL for upsert/resolution semantics, `AttributeConverter` for custom-valued enums. Flyway owns the schema (`schema-generate` off, ADR-0016); bulk import stays raw SQL/COPY (ADR-0006). No JPA/Hibernate.
- **Java 25 + Micronaut**, single module — read-only API, daily `@Scheduled` job, and gated historical import. Regex over per-document XML + ported bormeparser dictionaries, no Python. Micronaut over Spring Boot for low memory / fast startup. ADR-0005.
- **Virtual threads** for blocking work: `@ExecuteOn(TaskExecutors.BLOCKING)` (a virtual-thread-per-task executor on Java 25). High concurrency cheap on one VPS, no reactive complexity. ADR-0011.
- **Shared ingestion logic** in `domain`/`infrastructure` so resolution/idempotency are defined once across both write paths. ADR-0005/0007.
- Deployed via Docker on one cheap EU VPS; nightly `pg_dump`. ADR-0011.
