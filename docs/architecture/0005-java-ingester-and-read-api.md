# ADR-0005 — Three Java modules: shared library, hosted server, offline ingester

## Status

Accepted. *(Updated: single Java stack; daily incremental moved into the
server's scheduler; common code extracted to a shared library — see Context.)*

## Context

Two distinct workloads exist: (1) fetching and **parsing** BORME documents — regex over the
per-document XML, dictionary lookups, normalisation; and (2) serving a **read API** to
consumers, which must be hosted and stable. In addition the data must be kept current with a
**daily incremental** ingest.

An earlier draft put the parsing worker in **Python**; on inspection the work is regex over
structured XML ([ADR-0002](0002-txt-php-over-pdf-parsing.md)) plus dictionaries — not ML/NLP
— and the hard fuzzy matching lives in **PostgreSQL** and shared DB functions
([ADR-0007](0007-single-source-of-truth-entity-resolution.md)), independent of the worker's
language. The operator is Java-centric and values a single stack. Both the daily incremental
and the historical backfill need the *same* fetch/parse/normalise/persist logic, which must
not be duplicated.

## Decision

Build everything in **Java 25** as a **Gradle 9.5 multi-project** (version pinned via the
wrapper) with **three subprojects**:

- **`shared`** — a common library: the BOE client (summary enumeration + document fetch,
  XML→txt→PDF), the parser (regex + ported bormeparser dictionaries), the normalisation
  function, the domain model/DTOs, and the **`IngestionService`** that resolves
  (`resolve_company`/`resolve_person`) and persists with idempotency
  ([ADR-0007](0007-single-source-of-truth-entity-resolution.md)). It never exposes HTTP.
- **`server`** — a **Micronaut** app: the **read-only** HTTP API **and** the **daily
  incremental**, run in-process on the Micronaut scheduler (`@Scheduled`). The only mandatory
  server-hosted component, stateless over PostgreSQL.
- **`ingester`** — an offline CLI, runnable **locally/off-server**, that performs the
  **historical backfill** (bulk staging + merge).

`server` and `ingester` both depend on `shared` and call the same `IngestionService`, so the
daily and backfill paths behave identically. The parser **does not resolve identity**.

## Consequences

- **One language, one toolchain, one build** (Gradle), with common logic defined once in
  `shared` — directly supporting the single-source-of-truth goal
  ([ADR-0007](0007-single-source-of-truth-entity-resolution.md)).
- The daily incremental needs **no separate process or external scheduler** and **no HTTP
  ingest endpoint** — it is a scheduled bean inside the already-hosted server
  ([ADR-0006](0006-hybrid-write-path.md)).
- The read API is **purely read-only**; there is no write surface exposed over HTTP.
- The historical backfill still runs **offline** and never sits on the API's request path.
- **One-time cost:** porting bormeparser's regex/dictionary tables from Python to Java.
- If genuine ML/NLP extraction is ever needed, Python's ecosystem is no longer at hand — a
  deliberate trade, acceptable because the design uses deterministic parsing + DB-side fuzzy
  matching.

## Alternatives considered

- **Python ingester (earlier draft)** — reuses bormeparser directly, but adds a second
  language/toolchain and blocks code-sharing with the server; rejected for a single Java stack.
- **Daily incremental as a separate cron/process posting to an HTTP ingest API** — an extra
  moving part and an HTTP endpoint whose only caller would be our own scheduler; rejected in
  favour of an in-server `@Scheduled` job calling `shared` directly.
- **One module (no shared library)** — would duplicate fetch/parse/persist between server and
  ingester; rejected.
- **Micronaut vs Spring Boot** — Micronaut chosen for low memory / fast startup on a cheap VPS
  ([ADR-0011](0011-cheap-eu-vps-hosting.md)).
