# ADR-0014 — Hexagonal architecture (ports and adapters)

## Status

Accepted.

## Context

Mercator has several distinct I/O concerns around a small but meaningful domain (companies,
people, acts, addresses and the links between them): it fetches BORME documents over HTTP
with an XML→`txt.php`→PDF fallback ([ADR-0002](0002-txt-php-over-pdf-parsing.md)), parses and
normalises free Spanish prose, persists through PostgreSQL with DB-side entity resolution
([ADR-0007](0007-single-source-of-truth-entity-resolution.md)), serves a read-only HTTP API,
and runs both a daily incremental and an offline backfill ([ADR-0006](0006-hybrid-write-path.md)).
The same fetch/parse/normalise/persist logic is shared between `server` and `ingester`
([ADR-0005](0005-java-ingester-and-read-api.md)).

The goals, in order, are **clear isolation between layers so the code is easy to understand
and debug**, and **testability** — the domain/application logic should be exercisable without
a database or network. Where business logic lives, where I/O lives, and what crosses the
boundary should be obvious from the structure, so a failure can be localised quickly and the
core can be unit-tested at its ports. Keeping the core independent of I/O details (database,
network, Micronaut) follows from that, and brings a further benefit — an I/O choice can change
behind its boundary (e.g. the link-query implementation escalating to Apache AGE,
[ADR-0010](0010-postgresql-ctes-over-graph-db.md)). But **isolation, debuggability and
testability are the drivers, not architectural purity**.

## Decision

Adopt **hexagonal architecture (ports and adapters)** across the Java modules, applied
**pragmatically** — the unit of the pattern is the *layer boundary*, not the individual class.
The aim is that anyone reading the code can tell business logic from I/O at a glance.

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

**Pragmatic scope.** Define a port only where a real layer boundary is crossed (BORME source,
persistence/resolution, link queries, the inbound API/jobs). Do **not** manufacture ports for
internal helpers, wrap trivial value objects, or add indirection that does not separate a
genuine layer — that is ceremony, and it *hurts* readability rather than helping. When a port
would make the code harder to follow than a direct call, prefer the direct call. The test for
any abstraction here is simple: *does it make the layers clearer, the system easier to debug,
or the core easier to test?* If none of those, leave it out.

## Consequences

- **Layers are visibly separated**, so a reader can tell business logic from I/O at a glance
  and a failure can be localised to a layer quickly — the primary payoff.
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
