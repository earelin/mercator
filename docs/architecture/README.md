# Mercator — Architecture Decision Records

This folder records the significant architectural decisions for Mercator, one per file, in
[MADR](https://adr.github.io/madr/)-style format: **Status, Context, Decision,
Consequences, Alternatives considered**.

**Lifecycle:** an ADR is _Proposed (pending approval)_ and may be **freely updated while
Proposed**; only the maintainer marks one **Accepted**, and **Accepted ADRs are immutable**
(change them only via a new superseding ADR).

> **Note (2026-06).** ADR-0005/0006/0013/0014 were **amended in place** to record the
> single-module redesign (collapsing `shared`/`server`/`ingester` into one Micronaut module and
> moving the historical import to a gated in-server endpoint), with the change noted in each of
> their `## Status` lines. Incidental references to the removed `ingester`/`shared` module were
> also updated for consistency in ADR-0007/0015/0016/0018/0020. This is a maintainer-approved
> relaxation of the post-code immutability rule for a coordinated redesign. See `docs/CLAUDE.md`
> → _ADR lifecycle & approval_.

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-structured-xml-over-pdf-parsing.md) | Prefer structured text/XML over PDF parsing for Sección A | Accepted |
| [0003](0003-datosabiertos-rest-api-over-legacy-xml.md) | Use the `datosabiertos` REST summary API over legacy `xml.php` | Accepted |
| [0004](0004-postgresql-as-primary-datastore.md) | PostgreSQL as the single primary datastore | Accepted |
| [0005](0005-java-ingester-and-read-api.md) | Single Micronaut module: read API, daily scheduler, and historical-import endpoint | Accepted |
| [0006](0006-hybrid-write-path.md) | Two write paths, both in-server and direct to the DB | Accepted |
| [0007](0007-single-source-of-truth-entity-resolution.md) | Single source of truth for entity resolution | Accepted |
| [0008](0008-registry-coordinates-as-company-natural-key.md) | Registry coordinates (Hoja+province) as the company natural key | Accepted |
| [0009](0009-probabilistic-person-resolution.md) | Probabilistic person resolution with confidence scores | Accepted |
| [0010](0010-postgresql-ctes-over-graph-db.md) | PostgreSQL recursive CTEs over a graph database | Accepted |
| [0011](0011-cheap-eu-vps-hosting.md) | Single cheap EU VPS for hosting | Accepted |
| [0012](0012-reuse-bormeparser-dictionaries-gpl.md) | Reuse bormeparser dictionaries (GPL) | Accepted |
| [0013](0013-api-key-auth-and-config.md) | API key authentication, configured per environment | Accepted |
| [0014](0014-hexagonal-architecture.md) | Hexagonal architecture (ports and adapters) | Accepted |
| [0015](0015-auto-apply-fe-de-erratas-corrections.md) | Auto-apply Fe de erratas corrections, with an audit trail | Accepted |
| [0016](0016-database-schema-migrations.md) | Versioned database schema migrations with Flyway | Accepted |
| [0017](0017-observability-logging-and-alerting.md) | Observability: logging, metrics, heartbeat and alerting | Accepted |
| [0018](0018-boe-source-politeness-and-retry.md) | BOE source politeness and fail-soft retry policy | Accepted |
| [0019](0019-backup-restore-and-retention.md) | Tested backup/restore and retention policy | Accepted |
| [0020](0020-secrets-management.md) | Secrets management on a single VPS | Accepted |
| [0021](0021-borme-data-reuse-and-attribution.md) | BORME data reuse and attribution | Accepted |
