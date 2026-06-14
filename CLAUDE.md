# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

**Pre-implementation.** This repository currently contains only documentation (`docs/`),
the GPLv3 `LICENSE`, and `README.md`. There is no source code, build system, or tests yet.
The specs, features and ADRs in `docs/` are the authoritative design and **must be read
before writing code** — they define what to build and the constraints that govern it.

When you add the first code, also add its build/lint/test commands to this file.

## What Mercator is

Mercator turns Spain's official mercantile gazette — the **BORME** (*Boletín Oficial del
Registro Mercantil*) — into a queryable database and graph of companies, the people
associated with them (administrators, attorneys…), and the links between them (shared
administrators, shared registered address, multi-hop relationships). Its primary consumer is
a sibling **public-contracts** project that detects related bidders/awardees. The mandate is
**cheap and simple**: a single EU VPS, free public data, no commercial BORME API.

## Repository layout

A **Gradle 9.5 multi-project** (`settings.gradle.kts`, version pinned via the wrapper) with
three subprojects:

- `docs/` — specs, features, architecture (design source of truth; see below).
- `shared/` — common Java library (BOE client, parser, normalisation, `IngestionService`);
  depended on by both `server` and `ingester`.
- `server/` — Java 25 + Micronaut **read-only API** + the daily-incremental scheduler; the
  only server-hosted component.
- `ingester/` — Java offline CLI for the historical backfill; runs locally/off-server.

`shared/`, `server/` and `ingester/` are currently scaffolding placeholders (README only) —
no code yet.

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
- **Java 25** ingester (`ingester/`) — offline CLI for the historical backfill, run
  locally/off-server; regex over the per-document XML + ported bormeparser dictionaries; no
  Python. See ADR-0005.
- **Java 25 + Micronaut** `server/` — the **read-only** API **and** the daily-incremental job
  (Micronaut `@Scheduled`); the only server-hosted component (Micronaut chosen over Spring
  Boot for low memory/fast startup on a cheap VPS). See ADR-0005.
- **`shared/` library** — BOE client, parser, normalisation and the `IngestionService`; used
  by both `server` and `ingester` so resolution/idempotency are defined once. See ADR-0005/0007.
- **Gradle 9.5** multi-project build (pinned via the wrapper). See ADR-0005.
- Deployed via Docker on one cheap EU VPS; nightly `pg_dump`. See ADR-0011.

## Architecture invariants (easy to violate, hard to undo)

These are the load-bearing decisions; preserve them unless a new ADR supersedes them.

- **Ingest structured XML, not PDF.** Sección A documents are fetched as per-document XML
  (each summary item's `url_xml` / `xml.php`), whose `<texto>` is pre-segmented into
  `<p class="articulo">` (company header) and `<p class="parrafo">` (act block). Fallback
  order is XML → `txt.php` → PDF. Act *fields* are still free prose needing regex parsing.
  The summary also exposes `url_xml`/`url_html`/`url_pdf` for sections **A, B and C** back to
  2009 (Sección B = "otros actos publicados", currently out of scope). (ADR-0002)
- **Two write paths, both direct to the DB via the shared library.** Historical backfill
  (`ingester`, offline) bulk-loads via staging tables + an SQL merge; the daily incremental
  (`server`, Micronaut `@Scheduled`) writes in-process, row-by-row. There is **no HTTP ingest
  API** — the public API is read-only. (ADR-0006)
- **Entity resolution lives in exactly one place** — shared `resolve_company()` /
  `resolve_person()` PL/pgSQL functions, called by *both* write paths. The **parser never
  resolves identity**; it emits normalised-but-unresolved records only. (ADR-0007)
- **Company natural key = Hoja registral + province** (`UNIQUE (reg_hoja, province_code)`).
  The BORME publishes **no NIF**, so name matching is for search/cross-source only, never
  company identity. (ADR-0008)
- **Persons are probabilistic.** No identifier exists for people; person-derived links are
  **scored candidates with confidence**, never presented as facts. (ADR-0009)
- **Idempotency at two layers:** app-level `borme_log` short-circuit + DB-level
  `UNIQUE (borme_id, company_id, act_type, datos_registrales)` with `ON CONFLICT DO NOTHING`.
  Re-processing any document must be a no-op. (ADR-0006)
- **Bounded link queries in pure SQL** (indexed joins + recursive CTEs with path-array cycle
  detection). Apache AGE is an escalation only if a query needs >3 unbounded hops or blows
  `work_mem` — do not add it preemptively. (ADR-0010)
- **GDPR.** Suppress residual DNI/NIE from output; honour a suppression flag across
  re-ingestion; never claim the data is authentic (only the signed BORME PDF is). (Spec 7)
- **Fe de erratas are auto-applied with an audit trail.** A `FE_ERRATAS` act (parrafo opens
  with "Fe de erratas:") is parsed into a correction (target ref + before→after), then the
  shared ingestion step rewrites the target act/entity (re-resolving on a name fix) while
  retaining the pre-correction value (`act_correction`). The parser detects/structures but
  never applies. Un-matchable/ambiguous corrections are stored **unapplied + flagged**, never
  guessed; application is idempotent. (ADR-0015)
- **API auth + config.** The read API requires an **API key** (`X-API-Key`) in production,
  anonymous in local dev; auth is **environment-toggled, enabled by default, fail-closed**.
  All config is 12-factor (env vars + Micronaut environments, one artifact); secrets are
  injected at run time, never committed or baked into images. (ADR-0013)
- **Hexagonal architecture (ports and adapters), applied pragmatically.** The goal is **clear
  layer isolation (readability + debuggability) and testability**, not architectural purity.
  The domain/application core (in `shared`) depends on nothing outward — no Micronaut, no JDBC,
  no HTTP; dependencies point **inward only**. I/O lives in adapters behind core-owned ports
  (BORME source, persistence/resolution, link queries are driven ports; the API and the
  scheduled/CLI jobs are driving adapters). The DB-side resolution (ADR-0007) is the
  *implementation* of a core-owned resolution port, not an exception to the rule. Define a port
  only where a real layer boundary is crossed — don't manufacture ports for trivial internals;
  if an abstraction doesn't make the layers clearer, the system easier to debug, or the core
  easier to test, leave it out. (ADR-0014)

## Licensing constraint when porting prior art

The project is **GPLv3**; bormeparser's dictionaries are reused, so derived parser code stays
GPL-compatible. (ADR-0012)

`bormeparser`/`libreborme` (GPLv3) are **reference implementations, not runtime
dependencies** (unmaintained, PDF-oriented). Their regex/act/role/province dictionaries may
be **vendored/ported** under GPL; do not add them as live dependencies. (ADR-0012)
