# Mercator — Architecture Decision Records

This folder records the significant architectural decisions for Mercator, one per file, in
[MADR](https://adr.github.io/madr/)-style format: **Status, Context, Decision,
Consequences, Alternatives considered**.

**Lifecycle:** an ADR is _Proposed (pending approval)_ and may be **freely updated while
Proposed**; only the maintainer marks one **Accepted**, and **Accepted ADRs are immutable**
(change them only via a new superseding ADR).

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-txt-php-over-pdf-parsing.md) | Prefer structured text/XML over PDF parsing for Sección A | Accepted |
| [0003](0003-datosabiertos-rest-api-over-legacy-xml.md) | Use the `datosabiertos` REST summary API over legacy `xml.php` | Accepted |
| [0004](0004-postgresql-as-primary-datastore.md) | PostgreSQL as the single primary datastore | Accepted |
| [0005](0005-java-ingester-and-read-api.md) | Three Java modules: shared library, hosted server, offline ingester | Accepted |
| [0006](0006-hybrid-write-path.md) | Two write paths, both direct to the DB via the shared library | Accepted |
| [0007](0007-single-source-of-truth-entity-resolution.md) | Single source of truth for entity resolution | Accepted |
| [0008](0008-registry-coordinates-as-company-natural-key.md) | Registry coordinates (Hoja+province) as the company natural key | Proposed |
| [0009](0009-probabilistic-person-resolution.md) | Probabilistic person resolution with confidence scores | Accepted |
| [0010](0010-postgresql-ctes-over-graph-db.md) | PostgreSQL recursive CTEs over a graph database | Accepted |
| [0011](0011-cheap-eu-vps-hosting.md) | Single cheap EU VPS for hosting | Accepted |
| [0012](0012-reuse-bormeparser-dictionaries-gpl.md) | Reuse bormeparser dictionaries (GPL) | Accepted |
| [0013](0013-api-key-auth-and-config.md) | API key authentication, configured per environment | Accepted |
| [0014](0014-hexagonal-architecture.md) | Hexagonal architecture (ports and adapters) | Accepted |
| [0015](0015-auto-apply-fe-de-erratas-corrections.md) | Auto-apply Fe de erratas corrections, with an audit trail | Accepted |
