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
./gradlew check          # unit test + Checkstyle + CPD (no Docker; excludes integration)
```

The database must be running (`docker compose up -d`) before starting the server. The
`integration` suite stands up its own Postgres via Testcontainers, so the Docker daemon must be
available when running it.

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
- `./gradlew check` runs Checkstyle (shared config in `config/checkstyle/`) and CPD
  (duplication); keep both green.

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
- `docs/architecture/` — **why** (Architecture Decision Records).

See **[`docs/CLAUDE.md`](docs/CLAUDE.md)** for how the docs are organised, the doc-authoring
conventions, and the ADR lifecycle/approval rules.

## Planned tech stack (per the ADRs)

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

## Architecture invariants (easy to violate, hard to undo)

These are the load-bearing decisions; preserve them unless a new ADR supersedes them.

- **Ingest structured XML, not PDF.** Sección A documents are fetched as per-document XML
  (each summary item's `url_xml` / `xml.php`), whose `<texto>` is pre-segmented into
  `<p class="articulo">` (company header) and `<p class="parrafo">` (act block). Fallback
  order is XML → `txt.php` → PDF. Act *fields* are still free prose needing regex parsing.
  The summary also exposes `url_xml`/`url_html`/`url_pdf` for sections **A, B and C** back to
  2009 (Sección B = "otros actos publicados", currently out of scope). (ADR-0002)
- **Two write paths, both in-server and direct to the DB.** The historical/massive import
  bulk-loads via staging tables + an SQL merge, triggered by a **gated, authenticated admin
  endpoint** (disabled by default; async — `202` + job status); the daily incremental (Micronaut
  `@Scheduled`) writes in-process, row-by-row. The **public** API is read-only — the admin
  import endpoint is the only write surface. (ADR-0006)
- **Entity resolution lives in exactly one place** — the `resolve_company()` /
  `resolve_person()` PL/pgSQL functions, wrapped by the in-server ingestion service and called by
  *both* write paths. The **parser never resolves identity**; it emits normalised-but-unresolved
  records only. (ADR-0007)
- **Company natural key = Hoja registral + province** (`UNIQUE (reg_hoja, province_code)`).
  The BORME publishes **no NIF**, so name matching is for search/cross-source only, never
  company identity. (ADR-0008)
- **Persons are probabilistic.** No identifier exists for people; person-derived links are
  **scored candidates with confidence**, never presented as facts. (ADR-0009)
- **Idempotency at two layers:** app-level `borme_log` short-circuit + DB-level
  `UNIQUE (borme_id, company_id, act_type, datos_registrales, doc_seq)` with `ON CONFLICT DO
  NOTHING` (`doc_seq` = the act block's deterministic ordinal within its document, so two
  legitimately distinct same-type acts sharing one `datos_registrales` don't collide while
  re-processing the same document stays a no-op). Re-processing any document must be a no-op.
  (ADR-0006)
- **Bounded link queries in pure SQL** (indexed joins + recursive CTEs with path-array cycle
  detection). Apache AGE is an escalation only if a query needs >3 unbounded hops or blows
  `work_mem` — do not add it preemptively. (ADR-0010)
- **GDPR.** Suppress residual DNI/NIE from output; honour a suppression flag across
  re-ingestion; never claim the data is authentic (only the signed BORME PDF is). (Spec 7)
- **Fe de erratas are auto-applied with an audit trail.** A `FE_ERRATAS` act (parrafo opens
  with "Fe de erratas:") is parsed into a correction (target ref + before→after), then the
  in-server ingestion step rewrites the target act/entity (re-resolving on a name fix) while
  retaining the pre-correction value (`act_correction`). The parser detects/structures but
  never applies. Un-matchable/ambiguous corrections are stored **unapplied + flagged**, never
  guessed; application is idempotent. (ADR-0015)
- **API auth + config.** The read API requires an **API key** (`X-API-Key`) in production,
  anonymous in local dev; auth is **environment-toggled, enabled by default, fail-closed**. The
  **admin import endpoint** is stricter: **always** key-required (every environment, no dev
  exemption) and additionally gated by `mercator.imports.historical.enabled` (default off —
  absent/404 when disabled). All config is 12-factor (env vars + Micronaut environments, one
  artifact); secrets are injected at run time, never committed or baked into images. (ADR-0013)
- **Layered architecture, applied for simplicity.** The goal is **clear layer isolation
  (readability + debuggability) and testability**, not architectural purity. Three packages with
  **inward-only** dependencies: `…application` (Micronaut driving adapters + wiring) →
  `…infrastructure` (driven adapters) → `…domain` (model + application services). The domain core
  does not import the outer layers, and `jakarta.inject` (`@Singleton`/`@Inject`) lets its services
  be beans without `@Factory` boilerplate. **Domain data objects may double as DB entities and API
  bodies** — a domain record can carry `@MappedEntity`/`@Serdeable` and be persisted/serialized
  directly; a separate persistence row or API DTO is added **only where the shape genuinely
  differs** (a computed/hypermedia field, edge string-parsing). I/O still lives behind core-owned
  ports (BORME source, persistence/resolution, link queries are driven ports; the API, the
  scheduled daily job, and the import controller + async runner are driving adapters) — these aid
  the stub-based testing convention. The DB-side resolution (ADR-0007) is the *implementation* of a
  core-owned resolution port. Define a port only where a real layer boundary is crossed — don't
  manufacture ports for trivial internals. **Only the inward-only dependency direction** is enforced
  by an **ArchUnit** test (`LayeredArchitectureTest`, in `./gradlew check`); the core is no longer
  required to be framework-free. (ADR-0014)

## Licensing constraint when porting prior art

The project is **GPLv3**; bormeparser's dictionaries are reused, so derived parser code stays
GPL-compatible. (ADR-0012)

`bormeparser`/`libreborme` (GPLv3) are **reference implementations, not runtime
dependencies** (unmaintained, PDF-oriented). Their regex/act/role/province dictionaries may
be **vendored/ported** under GPL; do not add them as live dependencies. (ADR-0012)
