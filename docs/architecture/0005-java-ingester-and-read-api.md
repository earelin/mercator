# ADR-0005 — Single Micronaut module: read API, daily scheduler, and historical-import endpoint

*(Filename is legacy — kept to preserve inbound links. This ADR no longer describes a separate
ingester or shared library.)*

## Status

Accepted. *(Updated 2026-06: collapsed the three-module split — `shared`/`server`/`ingester` —
into a **single Micronaut module**; the offline backfill CLI is removed and the historical
import now runs in-server behind a gated admin endpoint. A maintainer-approved redesign for
simplicity; superseding-ADR immutability is relaxed for this amendment — see Context.)* *(Updated
2026-06: row-by-row database access uses **Micronaut Data JDBC** — see the persistence paragraph
under Decision. Maintainer-approved in-place amendment.)* *(Updated 2026-06: persistence entities
are the **domain objects themselves** (`@MappedEntity` on the domain record, no parallel row), and
blocking request handling runs on **Java virtual threads** via `@ExecuteOn(TaskExecutors.BLOCKING)`.
Maintainer-approved in-place amendment.)*

## Context

Two distinct workloads exist: (1) fetching and **parsing** BORME documents — regex over the
per-document XML, dictionary lookups, normalisation; and (2) serving a **read API** to
consumers, which must be hosted and stable. In addition the data must be kept current with a
**daily incremental** ingest, and a one-time **historical backfill** (2009→present) must be
run.

An earlier draft put the parsing worker in **Python**; on inspection the work is regex over
structured XML ([ADR-0002](0002-structured-xml-over-pdf-parsing.md)) plus dictionaries — not ML/NLP
— and the hard fuzzy matching lives in **PostgreSQL** and shared DB functions
([ADR-0007](0007-single-source-of-truth-entity-resolution.md)), independent of the worker's
language. The operator is Java-centric and values a single stack.

A subsequent draft split the Java into three Gradle subprojects — a `shared` library, a hosted
`server`, and an offline `ingester` CLI for the backfill. In practice the backfill is a modest
text job (low-single-digit-millions of acts, [Spec 8](../specs/08-non-functional.md)) that runs
rarely, and the single cheap VPS ([ADR-0011](0011-cheap-eu-vps-hosting.md)) already hosts the
server. Maintaining a second runnable artifact, a library boundary, and a CLI surface for an
operation that the hosted server can perform directly was **needless ceremony**. The operator
chose to **simplify to one module and one artifact**.

## Decision

Build everything as a **single Java 25 + Micronaut module** (a single-project Gradle 9.5 build,
version pinned via the wrapper). The one artifact hosts:

- the **read-only HTTP API** ([Spec 6](../specs/06-public-api.md));
- the **daily incremental**, run in-process on the Micronaut scheduler (`@Scheduled`); and
- the **historical / massive import**, triggered on demand via a **gated admin HTTP endpoint**
  (by date or by month), which runs the import asynchronously in the background.

Internal layering is by **package**, not by Gradle module
([ADR-0014](0014-hexagonal-architecture.md)): the framework-free domain/application core and
its driven adapters live in `net.earelin.mercator.domain` / `…infrastructure`; the Micronaut
driving adapters (controllers, the `@Scheduled` bean, wiring) live in `…application`. Both write
paths call the **same** ingestion logic and entity resolution
([ADR-0007](0007-single-source-of-truth-entity-resolution.md)), so the daily and backfill paths
behave identically. The parser **does not resolve identity**.

Row-by-row database access (the daily incremental, the `borme_log` idempotency record, and the
calls into the `resolve_*` functions of [ADR-0007](0007-single-source-of-truth-entity-resolution.md))
goes through **Micronaut Data JDBC** — compile-time `@JdbcRepository` interfaces with no reflection
or runtime proxies, fitting the low-memory/fast-startup goal — living in the `…infrastructure`
adapters. The **mapped entity is the domain object itself** (e.g. `BormeLogEntry` carries
`@MappedEntity`), not a parallel persistence row; a repository may also implement a core port
directly. Conflict/upsert and function-call semantics use explicit `@Query` SQL, and an
`AttributeConverter` covers any enum stored as a custom value. Flyway remains the single owner of
the schema ([ADR-0016](0016-database-schema-migrations.md)) (repository schema generation is off);
the **bulk** historical import keeps its raw staging-table `COPY` + SQL merge
([ADR-0006](0006-hybrid-write-path.md)) rather than per-row repository writes. No JPA/Hibernate.

Blocking request handling runs on **Java virtual threads**: controllers (and future scheduled /
async jobs) that do blocking I/O are annotated `@ExecuteOn(TaskExecutors.BLOCKING)`, which on Java
25 is a virtual-thread-per-task executor automatically. This keeps high request concurrency cheap
on the single VPS ([ADR-0011](0011-cheap-eu-vps-hosting.md)) without adopting a reactive
programming model — consistent with the "keep it simple" mandate.

## Consequences

- **One language, one toolchain, one build, one artifact** — the simplest thing that hosts every
  workload on the single VPS.
- The daily incremental needs **no separate process or external scheduler** — it is a scheduled
  bean inside the server ([ADR-0006](0006-hybrid-write-path.md)).
- The historical backfill no longer needs a CLI or a local run: an operator triggers it through
  the **admin import endpoint**, which is **disabled by default** and **always authenticated**
  ([ADR-0013](0013-api-key-auth-and-config.md)), and the import runs in-server over the bulk
  staging + merge write path.
- The **public** read API stays read-only; the only write surface is the gated admin endpoint —
  a deliberate, narrow exception to "no HTTP write surface", isolated by config + auth.
- **One-time cost:** porting bormeparser's regex/dictionary tables from Python to Java.
- If genuine ML/NLP extraction is ever needed, Python's ecosystem is no longer at hand — a
  deliberate trade, acceptable because the design uses deterministic parsing + DB-side fuzzy
  matching.

## Alternatives considered

- **Three Java modules (`shared`/`server`/`ingester`)** — a prior version of this ADR. The
  library boundary and a second CLI artifact added ceremony without payoff for a rarely-run,
  modest-volume backfill on a single-instance deployment; superseded by the single module.
- **Python ingester (earliest draft)** — reuses bormeparser directly, but adds a second
  language/toolchain and blocks code-sharing with the server; rejected for a single Java stack.
- **Historical backfill as a separate offline CLI** — keeps the write path entirely off the
  HTTP surface, but costs a second artifact and a local run procedure; rejected in favour of a
  gated in-server endpoint, accepting the narrow, authenticated write surface.
- **Micronaut vs Spring Boot** — Micronaut chosen for low memory / fast startup on a cheap VPS
  ([ADR-0011](0011-cheap-eu-vps-hosting.md)).
