# ADR-0014 — Hexagonal architecture (ports and adapters)

## Status

Accepted. *(Updated 2026-06: the layers are now separated by **package** within a single module
rather than across `shared`/`server`/`ingester`; the offline-CLI driving adapter is replaced by
the in-server import controller. Maintainer-approved redesign amendment — see
[ADR-0005](0005-java-ingester-and-read-api.md).)* *(Updated 2026-06: the domain core may carry the
vendor-neutral `jakarta.inject` (JSR-330) DI annotations to drop `@Factory` boilerplate; only
Micronaut-specific (`io.micronaut.*`) types stay forbidden in the core. Maintainer-approved
in-place amendment — see the **Domain core** bullet under Decision.)* *(Updated 2026-06: the
**driven adapters** (`…infrastructure`) may now use infrastructure-facing Micronaut tooling —
`io.micronaut.*` is forbidden **only** in the domain core, not the whole non-application surface;
the layer boundaries are now enforced by an ArchUnit test. Maintainer-approved in-place
amendment.)* *(Updated 2026-06 — **simplification**: the domain core is **no longer required to be
framework-free**. A domain data object may carry persistence (`@MappedEntity`) and serialization
(`@Serdeable`) annotations and serve directly as the DB entity / API body; a separate persistence
row or DTO is added only where the shape genuinely differs. Only the **inward-only dependency
direction** is still enforced by ArchUnit. Maintainer-approved in-place amendment — see the
**Domain core** bullet.)*

## Context

Mercator has several distinct I/O concerns around a small but meaningful domain (companies,
people, acts, addresses and the links between them): it fetches BORME documents over HTTP
with an XML→`txt.php`→PDF fallback ([ADR-0002](0002-structured-xml-over-pdf-parsing.md)), parses and
normalises free Spanish prose, persists through PostgreSQL with DB-side entity resolution
([ADR-0007](0007-single-source-of-truth-entity-resolution.md)), serves a read-only HTTP API,
and runs both a daily incremental and a historical backfill ([ADR-0006](0006-hybrid-write-path.md)).
The same fetch/parse/normalise/persist logic backs both write paths inside one module
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

- **Domain core** (in the `net.earelin.mercator.domain` packages) — the model and the
  use-case/application services (parsing, normalisation, the ingestion service). It depends on no
  *outer layer* — it must not import `…infrastructure` or `…application`; dependencies point
  **inward only**. It is **not** required to be framework-free: a domain **data object may carry
  persistence and serialization annotations** (`@MappedEntity`, `@Id`, `@MappedProperty`,
  `@Serdeable`) and be used directly as the Micronaut Data entity and/or the API body — for a small
  domain, one annotated record beats a parallel persistence row + DTO + the mapping between them. A
  separate persistence row or API DTO is introduced **only where the shape genuinely differs** (a
  computed/hypermedia field, or edge string-parsing at the controller). Application services carry
  `jakarta.inject` `@Singleton`/`@Inject` so they are auto-discovered as beans without a
  hand-written `@Factory`; constructors stay public and usable from a plain `new` in unit tests.
  Where an enum is stored as a custom value, an `AttributeConverter` (a `@Singleton` living in the
  core, beside the enum) keeps the column mapping correct. **Only the inward-only dependency
  direction is enforced** by the **ArchUnit** `LayeredArchitectureTest` (in `./gradlew check`); the
  core is no longer asserted to be free of `io.micronaut`/JDBC types.
- **Ports** — interfaces *owned by the core* expressing what it needs and offers:
  - *Driven (outbound) ports* — e.g. a `BormeGateway` (enumerate summary + fetch a document,
    encapsulating the XML→txt→PDF fallback), and a persistence/resolution port that exposes
    `resolve_company` / `resolve_person` / `resolve_address` and the upserts.
  - *Driving (inbound) ports* — the use-case interfaces the API and jobs call (query
    services, the ingestion use case).
- **Adapters** — implementations at the edges, depending **on** the core, never the reverse:
  - *Driven adapters* (in `net.earelin.mercator.infrastructure`): the BOE HTTP client; the
    PostgreSQL/JDBC persistence adapter that invokes the PL/pgSQL resolution functions and link
    queries; the in-memory import-job store. These **may use infrastructure-facing Micronaut
    tooling** where it pulls its weight (config binding, the declarative HTTP client,
    caching/retry); only the *driving*-side framework concerns (controllers, the scheduler) are
    reserved to the application layer. The unit-test isolation still holds: an adapter is exercised
    against its real backing tech, while the core is tested with a hand-written stub of the port.
  - *Driving adapters* (in `net.earelin.mercator.application`): the Micronaut HTTP controllers
    (read API, under `application.rest`; the gated admin import endpoint under
    `application.rest.admin.imports`), the `@Scheduled` daily-incremental bean, and the
    historical-import controller + async runner driving the bulk staging-table + merge path.
- **Wiring** — Micronaut dependency injection composes ports to adapters. A `@Factory` method in
  the `…application` packages is the right tool when construction is non-trivial (a bean built from
  configuration, a choice between implementations, an object that must not know it is a bean); a
  framework-free core service that simply needs its ports injected instead carries a
  `jakarta.inject` `@Singleton` and is auto-discovered (see the **Domain core** bullet).
  Micronaut-specific types (`io.micronaut.*`) are confined to the **outer layers** — the driving
  adapters + wiring (`…application`) and, where it earns its keep, the driven adapters
  (`…infrastructure`); they are **never** allowed in the domain core.

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
- The package split ([ADR-0005](0005-java-ingester-and-read-api.md)) maps cleanly onto
  core+ports (`…domain`) + driven-adapters (`…infrastructure`) vs. driving-adapters+wiring
  (`…application`), and the two write paths become two driving usages over the same ingestion use
  case — the `@Scheduled` daily bean and the import controller's async runner.
- **Mapping kept minimal:** because a domain object may itself be the `@MappedEntity` / API body,
  there is normally **no** domain↔DTO↔persistence-row triplication — a DTO or persistence row is
  added only where the shape genuinely differs. This trades a little theoretical isolation
  (framework annotations sit on domain types) for materially less code in a small domain.

## Alternatives considered

- **Layered / n-tier (controller → service → repository)** — familiar, but tends to let
  framework and persistence types leak upward into the service layer; weaker isolation and
  harder to test the core in isolation. Rejected.
- **Framework-centric (Micronaut annotations throughout, anemic services)** — fastest to
  write, but couples the domain to the framework and the DB, undermining the swappability
  (AGE escalation, source/format changes) and testability we want. Rejected — note this is
  distinct from admitting the *vendor-neutral* `jakarta.inject` standard into the core, which
  carries no framework types and is allowed (see the **Domain core** bullet).
- **Full Clean/Onion architecture with strict use-case interactors everywhere** — same intent
  as hexagonal but heavier ceremony than a project this size warrants; we take ports/adapters
  and the pragmatic-scope rule instead. Rejected as over-engineering for V1.
