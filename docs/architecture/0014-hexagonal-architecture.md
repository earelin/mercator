# ADR-0014 — Hexagonal architecture (ports and adapters)

## Status

Proposed (pending approval).

## Context

Mercator has several distinct I/O concerns around a small but meaningful domain (companies,
people, acts, addresses and the links between them): it fetches BORME documents over HTTP
with an XML→`txt.php`→PDF fallback ([ADR-0002](0002-txt-php-over-pdf-parsing.md)), parses and
normalises free Spanish prose, persists through PostgreSQL with DB-side entity resolution
([ADR-0007](0007-single-source-of-truth-entity-resolution.md)), serves a read-only HTTP API,
and runs both a daily incremental and an offline backfill ([ADR-0006](0006-hybrid-write-path.md)).
The same fetch/parse/normalise/persist logic is shared between `server` and `ingester`
([ADR-0005](0005-java-ingester-and-read-api.md)).

We want the **domain and application logic to be independent of those I/O details** — so it
is testable without a database or network, so the BORME source and the format-fallback can
evolve behind a stable contract, and so the framework (Micronaut) is a deployment detail
rather than something the core depends on. We also want the link-query implementation to be
swappable should it ever escalate to Apache AGE ([ADR-0010](0010-postgresql-ctes-over-graph-db.md)).

## Decision

Adopt **hexagonal architecture (ports and adapters)** across the Java modules.

- **Domain core** (in `shared`) — the model and the use-case/application services
  (parsing, normalisation, the `IngestionService`). It depends on **nothing** outward: no
  Micronaut, no JDBC, no HTTP client. Dependencies point **inward only**.
- **Ports** — interfaces *owned by the core* expressing what it needs and offers:
  - *Driven (outbound) ports* — e.g. a `BormeGateway` (enumerate summary + fetch a document,
    encapsulating the XML→txt→PDF fallback), and a persistence/resolution port that exposes
    `resolve_company` / `resolve_person` / `resolve_address` and the upserts.
  - *Driving (inbound) ports* — the use-case interfaces the API and jobs call (query
    services, the ingestion use case).
- **Adapters** — implementations at the edges, depending **on** the core, never the reverse:
  - *Driven adapters* (in `shared`): the BOE HTTP client; the PostgreSQL/JDBC persistence
    adapter that invokes the PL/pgSQL resolution functions and link queries.
  - *Driving adapters*: in `server`, the Micronaut HTTP controllers (read API) and the
    `@Scheduled` daily-incremental bean; in `ingester`, the offline CLI and its bulk
    staging-table + merge path.
- **Wiring** — Micronaut dependency injection composes ports to adapters at the application
  boundary (`server`/`ingester`). Framework annotations live in the adapters and wiring,
  **not** in the domain core.

**Reconciliation with DB-side resolution ([ADR-0007](0007-single-source-of-truth-entity-resolution.md)).**
Entity resolution intentionally lives in PL/pgSQL, which sits in tension with "all domain
logic in the core". We resolve it as follows: the **core owns the resolution *port*** (the
contract and its semantics — conservative bias, confidence, idempotency), and the
**PostgreSQL adapter is its implementation**. The DB is treated as a deliberately *rich
driven adapter* because Postgres-native fuzzy matching and a single source of truth are
load-bearing invariants that outrank hexagonal purity here. The core still defines and
depends only on the port; it never embeds SQL.

**Pragmatic scope.** Apply the pattern where a real boundary exists (BORME source,
persistence/resolution, link queries, the inbound API/jobs). Do **not** manufacture ports for
internal helpers or wrap trivial value objects — that is ceremony, not isolation. The goal is
testable, swappable edges, not maximal indirection on a small codebase.

## Consequences

- The domain core is **unit-testable with no database or network** — adapters are mocked at
  the ports; integration tests exercise the real PostgreSQL/HTTP adapters.
- I/O details **swap behind ports**: the XML→txt→PDF fallback lives inside the `BormeGateway`
  adapter; a future Apache AGE escalation replaces the link-query adapter without touching the
  core or the API ([ADR-0010](0010-postgresql-ctes-over-graph-db.md)).
- Micronaut stays a **boundary concern**, preserving fast startup / low memory on the cheap
  VPS ([ADR-0011](0011-cheap-eu-vps-hosting.md)) without coupling the core to it.
- The `shared`/`server`/`ingester` split ([ADR-0005](0005-java-ingester-and-read-api.md)) maps
  cleanly onto core+ports+driven-adapters (`shared`) vs. driving-adapters+wiring
  (`server`/`ingester`), and the two write paths become two driving adapters over the same
  ingestion use case.
- **Cost:** more interfaces and mapping (domain objects ↔ DTOs ↔ persistence rows) than a
  layered design — accepted as the price of isolation, and bounded by the pragmatic-scope rule.

## Alternatives considered

- **Layered / n-tier (controller → service → repository)** — familiar, but tends to let
  framework and persistence types leak upward into the service layer; weaker isolation and
  harder to test the core in isolation. Rejected.
- **Framework-centric (Micronaut annotations throughout, anemic services)** — fastest to
  write, but couples the domain to the framework and the DB, undermining the swappability
  (AGE escalation, source/format changes) and testability we want. Rejected.
- **Full Clean/Onion architecture with strict use-case interactors everywhere** — same intent
  as hexagonal but heavier ceremony than a project this size warrants; we take ports/adapters
  and the pragmatic-scope rule instead. Rejected as over-engineering for V1.
