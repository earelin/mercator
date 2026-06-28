# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

**Early implementation.** A single-project Gradle build (one Micronaut application artifact). The
document-fetch/parse/cache/persistence layers and the full Flyway schema (a single baseline
migration, `V1.0.0__baseline.sql`) exist; the ingestion service, resolution functions, read API
and the historical-import engine are still to be built
(the import endpoint is scaffolded). The specs, features and ADRs in `docs/` are the
authoritative design and **must be read before writing code** — they define what to build and
the constraints that govern it.

## Build, lint and test commands

```bash
./gradlew build          # compile + test
./gradlew test           # run the fast unit tests only
./gradlew integration    # run the integration tests (controllers + adapters; needs Docker)
./gradlew run            # start the Micronaut server locally
./gradlew check          # unit test + Checkstyle + PMD + CPD + Error Prone/NullAway + SpotBugs/FindSecBugs (no Docker; excludes integration)
```

The database must be running (`docker compose up -d`) before starting the server. The
`integration` suite stands up its own Postgres via Testcontainers, so the Docker daemon must be
available when running it.

## Scripts

Two helper scripts live in `scripts/` (run from anywhere — they `cd` to the repo root):

- `scripts/ci.sh` — the **local CI pipeline**, also wired as a git pre-push hook (see
  `.githooks/pre-push`; enable with `git config core.hooksPath .githooks`). It statically checks
  every Markdown file (markdownlint-cli2 formatting, lychee internal + external links/anchors,
  `mmdc` Mermaid syntax), lints Docker Compose files (`dclint` via `npx`), runs `./gradlew check`
  and `./gradlew build`, lints the Flyway SQL (`sqlfluff` over `src/main/resources/db/migration`),
  validates the OpenAPI contract for structure + security (`spectral` with `spectral:oas` +
  the OWASP ruleset over `docs/specs/api.openapi.yaml`), and runs a **SAST security scan**
  (`opengrep`, the open-source Semgrep fork) over the production sources `src/main` with the
  Semgrep-compatible `p/java` + `p/secrets` + `p/security-audit` rulesets — scoped to `src/main`
  like SpotBugs, since test fixtures legitimately do "unsafe" things. Finally it lints the GitHub
  Actions workflows (`actionlint`, which also shellchecks the `run:` scripts when `shellcheck` is on
  PATH). Skip external link checks with `CHECK_EXTERNAL=0`. Requires markdownlint-cli2, lychee, mmdc
  (+ a system Chrome/Chromium), npx, sqlfluff, opengrep, and actionlint installed locally (the
  opengrep ruleset fetch needs network on first run, then caches).
- `scripts/api-conformance.sh` — the heavyweight **dynamic** API check, deliberately kept out of
  `ci.sh`. It drives a *running* server with property-based cases generated from the OpenAPI doc
  (`schemathesis`) and asserts responses conform — drift (status code / schema / content type /
  headers) and security (malformed-input rejection, admin-endpoint auth enforcement per ADR-0013).
  Needs a reachable server (`./gradlew run` first); configure via `MERCATOR_BASE_URL` (default
  `http://localhost:8080`), `MERCATOR_API_KEY` (sent as `X-API-Key`), and
  `SCHEMATHESIS_MAX_EXAMPLES` (default 25 per operation). Requires `schemathesis` (`st`) installed.

## Code style

- **Comment sparingly.** Write self-explanatory code (clear names, small methods) and let it carry
  the intent. Add a comment only when it earns its place: a non-obvious *why* (a rationale, a
  workaround, a spec/ADR reference, a subtle invariant). Do **not** narrate *what* the code already
  says, restate the method or field name, or leave section-divider banners, changelog notes, or
  TODOs-as-documentation. Prefer deleting a stale comment over updating it. Match the comment density
  of the surrounding code — when in doubt, fewer.

## Testing conventions

- **Prefer stubs over mocks.** Drive behaviour through stubbed inputs and assert on observable
  outcomes / captured state — not on interactions via `verify()`. For stateful or side-effecting
  collaborators (a cache, a recorder, a call counter), use a small hand-written stub double that
  captures state and assert on that state; reserve Mockito for **stubbing** (`when(…).thenReturn(…)`)
  stateless inputs, not interaction verification.
- **AssertJ** (`assertThat`) for assertions, not native JUnit assertions; **assertj-db** for
  database-backed checks.
- Test method names are **snake_case**.
- **Two source sets (JVM Test Suite plugin).** `src/test` holds the fast **unit** tests (the
  `domain` core plus the pure-logic/in-memory `infrastructure` ones) — no Docker, run by
  `./gradlew test`/`check`. `src/integration` holds the **integration** tests that cross a process
  boundary: the controllers over Micronaut's embedded HTTP server (driven with **REST Assured**),
  the JDBC adapters against a real Postgres (Testcontainers), and the HTTP transport over a socket.
  Run them with `./gradlew integration` (needs Docker); they are deliberately **not** part of
  `check`.
- `./gradlew check` runs Checkstyle (shared config in `config/checkstyle/`), PMD (curated ruleset
  in `config/pmd/`) and CPD (duplication); keep all three green. It also compiles with **Error Prone**
  (javac bug-pattern checks) and **NullAway** (nullness analysis, configured inline in
  `build.gradle.kts`): NullAway runs on the production `main` sources only, treats the
  `net.earelin.mercator` packages as `@NonNull` by default, and fails the build on an unannotated
  nullable dereference — annotate genuinely-nullable record components, params and returns with the
  Micronaut `io.micronaut.core.annotation.@Nullable` already used across the codebase.
- `./gradlew check` also runs **SpotBugs** with the **Find Security Bugs** detector pack (bytecode
  analysis: bugs + security patterns like injection, weak crypto, SSRF, XXE). Like NullAway it runs
  on the production `main` sources only (test/integration fixtures trip the security detectors with
  deliberately "unsafe" code). The exclude filter lives in `config/spotbugs/exclude.xml` — it drops
  Micronaut's generated classes plus a few scoped, justified false positives / accepted trade-offs;
  prefer fixing a finding over widening the filter, and keep each filter entry commented.

## What Mercator is

Mercator turns Spain's official mercantile gazette — the **BORME** (*Boletín Oficial del
Registro Mercantil*) — into a queryable database and graph of companies, the people
associated with them (administrators, attorneys…), and the links between them (shared
administrators, shared registered address, multi-hop relationships). Its primary consumer is
a sibling **public-contracts** project that detects related bidders/awardees. The mandate is
**cheap and simple**: a single EU VPS, free public data, no commercial BORME API.

## Repository layout

A **single-project Gradle 9.5** build (`settings.gradle.kts`, version pinned via the wrapper)
producing one Java 25 + Micronaut server artifact. Sources live at the repo root under `src/`,
with layers separated by **package** (not by Gradle module):

- `docs/` — specs, features, architecture (design source of truth; see below).
- `net.earelin.mercator.domain.*` — the domain/application core: model, ports, and the
  ingestion/normalisation/parsing logic. Depends on no *other layer* (no infrastructure/application
  imports), but a domain data object **may** carry persistence (`@MappedEntity`) and serialization
  (`@Serdeable`) annotations and serve directly as the DB entity / API body — a separate DTO is
  introduced only where the shape genuinely differs (see the simplification note in ADR-0014).
- `net.earelin.mercator.infrastructure.*` — driven adapters (BOE HTTP client, document cache,
  extractors, JDBC persistence). May use **infrastructure-facing Micronaut tooling** where it
  earns its keep (e.g. config binding, the declarative HTTP client, caching/retry) — but never
  the driving-side concerns (controllers, the scheduler), which belong to `application`.
- `net.earelin.mercator.application.*` — the Micronaut driving adapters + wiring: the
  **read-only** API and the gated historical-import endpoint (REST controllers live under
  `application.rest`, with admin-only endpoints under `application.rest.admin` — e.g. the import
  endpoint at `application.rest.admin.imports`), the daily-incremental `@Scheduled` job, and the
  `@Factory` beans that compose the core/infra objects.

## Documentation

The authoritative design lives in `docs/` (read before writing code):

- `docs/specs/` — **what** the system does.
- `docs/features/` — **how** each spec is implemented, plus the implementation-issue backlog.
- `docs/architecture/` — **why** (Architecture Decision Records). Start at
  **[`docs/architecture/README.md`](docs/architecture/README.md)** — besides indexing the ADRs it
  carries a synthesised **current-state architecture overview** (the recommended entry point for the
  system's shape and the rationale behind it).

See **[`docs/CLAUDE.md`](docs/CLAUDE.md)** for how the docs are organised, the doc-authoring
conventions, and the ADR lifecycle/approval rules.

## Planned tech stack (per the ADRs)

For the full rationale and how these pieces fit together, see the architecture overview in
[`docs/architecture/README.md`](docs/architecture/README.md).

- **PostgreSQL 18** is the single datastore (`pg_trgm`, `fuzzystrmatch`, `unaccent`). No
  second datastore initially. See ADR-0004.
- **Micronaut Data JDBC** (compile-time repositories, HikariCP pool) is the row-by-row
  database-access layer — `@JdbcRepository` interfaces in `infrastructure.persistence` over
  `@MappedEntity` **domain objects** (the entity is the domain record, not a parallel row class),
  with custom `@Query` SQL where conflict/upsert or resolution-function semantics need it, and an
  `AttributeConverter` for any enum stored as a custom value. Flyway owns the schema
  (`schema-generate` off, ADR-0016); the bulk historical-import path stays raw SQL/COPY (ADR-0006).
  No JPA/Hibernate.
- **Java 25 + Micronaut** single module — the **read-only** API, the daily-incremental job
  (Micronaut `@Scheduled`), **and** the historical/massive import behind a gated admin endpoint;
  regex over the per-document XML + ported bormeparser dictionaries, no Python. Micronaut chosen
  over Spring Boot for low memory/fast startup on a cheap VPS. See ADR-0005.
- **Java virtual threads** back the blocking work: controllers (and future scheduled/async jobs)
  doing blocking I/O run on the Micronaut `blocking` executor via `@ExecuteOn(TaskExecutors.BLOCKING)`,
  which on Java 25 is a virtual-thread-per-task executor automatically. Keeps high concurrency cheap
  on one small VPS (ADR-0011) without reactive complexity.
- **Shared ingestion logic** — BOE client, parser, normalisation and the ingestion service live
  in the `domain`/`infrastructure` packages so resolution/idempotency are defined once and used
  by both write paths. See ADR-0005/0007.
- **Gradle 9.5** single-project build (pinned via the wrapper). See ADR-0005.
- Deployed via Docker on one cheap EU VPS; nightly `pg_dump`. See ADR-0011.
