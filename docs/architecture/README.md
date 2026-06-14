# Mercator — Architecture Decision Records

This folder records the significant architectural decisions for Mercator, one per file, in
[MADR](https://adr.github.io/madr/)-style format: **Status, Context, Decision,
Consequences, Alternatives considered**.

**Lifecycle:** an ADR is _Proposed (pending approval)_ and may be **freely updated while
Proposed**; only the maintainer marks one **Accepted**, and **Accepted ADRs are immutable**
(change them only via a new superseding ADR). All ADRs below are currently _Proposed_.

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Proposed |
| [0002](0002-txt-php-over-pdf-parsing.md) | Prefer structured text/XML over PDF parsing for Sección A | Proposed |
| [0003](0003-datosabiertos-rest-api-over-legacy-xml.md) | Use the `datosabiertos` REST summary API over legacy `xml.php` | Proposed |
| [0004](0004-postgresql-as-primary-datastore.md) | PostgreSQL as the single primary datastore | Proposed |
| [0005](0005-java-ingester-and-read-api.md) | Three Java modules: shared library, hosted server, offline ingester | Proposed |
| [0006](0006-hybrid-write-path.md) | Two write paths, both direct to the DB via the shared library | Proposed |
| [0007](0007-single-source-of-truth-entity-resolution.md) | Single source of truth for entity resolution | Proposed |
| [0008](0008-registry-coordinates-as-company-natural-key.md) | Registry coordinates (Hoja+province) as the company natural key | Proposed |
| [0009](0009-probabilistic-person-resolution.md) | Probabilistic person resolution with confidence scores | Proposed |
| [0010](0010-postgresql-ctes-over-graph-db.md) | PostgreSQL recursive CTEs over a graph database | Proposed |
| [0011](0011-cheap-eu-vps-hosting.md) | Single cheap EU VPS for hosting | Proposed |
| [0012](0012-reuse-bormeparser-dictionaries-gpl.md) | Reuse bormeparser dictionaries (GPL) | Proposed |
| [0013](0013-api-key-auth-and-config.md) | API key authentication, configured per environment | Proposed |
