# Mercator — Features

This folder describes **how** Mercator implements each [specification](../specs/README.md),
at a functional level, and breaks each feature into a sized **issue index** ready to become
GitHub issues.

Every feature doc follows the same template:

1. **Summary**
2. **Related specs / ADRs**
3. **Functional behaviour**
4. **Data flow**
5. **Inputs / outputs**
6. **Edge cases**
7. **Acceptance criteria**
8. **Implementation issues** — a sized, ordered checklist.

> No GitHub issues are created yet. The `## Implementation issues` checklists are the
> backlog from which issues will be created in a later phase.
>
> **All features below are in scope for V1** — there is no reduced MVP. V1 is full-featured:
> the complete act-type catalogue ([Spec 3](../specs/03-extraction.md)), link detection, the
> contracts integration and the data-protection tooling.

## Feature index & traceability

| Feature | Realises specs | Key ADRs |
|---------|----------------|----------|
| [summary-enumeration](summary-enumeration.md) | [1](../specs/01-data-sources.md), [2](../specs/02-ingestion.md) | [0003](../architecture/0003-datosabiertos-rest-api-over-legacy-xml.md), [0018](../architecture/0018-boe-source-politeness-and-retry.md) |
| [document-fetch](document-fetch.md) | [1](../specs/01-data-sources.md), [2](../specs/02-ingestion.md), [7](../specs/07-data-protection.md) | [0002](../architecture/0002-structured-xml-over-pdf-parsing.md), [0018](../architecture/0018-boe-source-politeness-and-retry.md) |
| [act-parsing](act-parsing.md) | [3](../specs/03-extraction.md) | [0002](../architecture/0002-structured-xml-over-pdf-parsing.md), [0012](../architecture/0012-reuse-bormeparser-dictionaries-gpl.md) |
| [errata-corrections](errata-corrections.md) | [2](../specs/02-ingestion.md), [3](../specs/03-extraction.md), [4](../specs/04-data-model.md), [7](../specs/07-data-protection.md) | [0015](../architecture/0015-auto-apply-fe-de-erratas-corrections.md), [0007](../architecture/0007-single-source-of-truth-entity-resolution.md) |
| [entity-extraction-normalisation](entity-extraction-normalisation.md) | [3](../specs/03-extraction.md), [4](../specs/04-data-model.md) | [0007](../architecture/0007-single-source-of-truth-entity-resolution.md) |
| [database-schema](database-schema.md) | [2](../specs/02-ingestion.md), [4](../specs/04-data-model.md) | [0004](../architecture/0004-postgresql-as-primary-datastore.md), [0006](../architecture/0006-hybrid-write-path.md), [0008](../architecture/0008-registry-coordinates-as-company-natural-key.md), [0016](../architecture/0016-database-schema-migrations.md) |
| [entity-resolution](entity-resolution.md) | [4](../specs/04-data-model.md), [5](../specs/05-link-detection.md) | [0007](../architecture/0007-single-source-of-truth-entity-resolution.md), [0008](../architecture/0008-registry-coordinates-as-company-natural-key.md), [0009](../architecture/0009-probabilistic-person-resolution.md) |
| [historical-backfill](historical-backfill.md) | [2](../specs/02-ingestion.md) | [0006](../architecture/0006-hybrid-write-path.md), [0018](../architecture/0018-boe-source-politeness-and-retry.md) |
| [daily-incremental](daily-incremental.md) | [2](../specs/02-ingestion.md) | [0006](../architecture/0006-hybrid-write-path.md), [0017](../architecture/0017-observability-logging-and-alerting.md) |
| [read-api](read-api.md) | [6](../specs/06-public-api.md) | [0005](../architecture/0005-java-ingester-and-read-api.md), [0013](../architecture/0013-api-key-auth-and-config.md) |
| [link-queries](link-queries.md) | [5](../specs/05-link-detection.md) | [0010](../architecture/0010-postgresql-ctes-over-graph-db.md) |
| [contracts-integration](contracts-integration.md) | [5](../specs/05-link-detection.md) | [0009](../architecture/0009-probabilistic-person-resolution.md) |
| [data-protection](data-protection.md) | [7](../specs/07-data-protection.md) | [0011](../architecture/0011-cheap-eu-vps-hosting.md), [0021](../architecture/0021-borme-data-reuse-and-attribution.md), [0019](../architecture/0019-backup-restore-and-retention.md) |
| [deployment-operations](deployment-operations.md) | [8](../specs/08-non-functional.md) | [0011](../architecture/0011-cheap-eu-vps-hosting.md), [0013](../architecture/0013-api-key-auth-and-config.md), [0016](../architecture/0016-database-schema-migrations.md), [0017](../architecture/0017-observability-logging-and-alerting.md), [0019](../architecture/0019-backup-restore-and-retention.md), [0020](../architecture/0020-secrets-management.md) |

**Cross-cutting ADRs** (apply across all features, not tied to one row): [0001](../architecture/0001-record-architecture-decisions.md)
(ADR process), [0005](../architecture/0005-java-ingester-and-read-api.md) (module layout),
[0014](../architecture/0014-hexagonal-architecture.md) (ports & adapters layering),
[0020](../architecture/0020-secrets-management.md) (secrets). The hexagonal layering of
[ADR-0014](../architecture/0014-hexagonal-architecture.md) has no single feature doc; it governs
how every feature's I/O sits behind a core-owned port.

**V1 pipeline (end to end):** summary-enumeration → document-fetch → act-parsing (full act
catalogue, incl. errata-corrections) → entity-extraction-normalisation → database-schema → entity-resolution →
historical-backfill (gated in-server import endpoint) + daily-incremental (in-server scheduler) →
read-api + link-queries, with contracts-integration and data-protection, all deployed per
deployment-operations. The fetch/parse/normalise/resolve logic lives in the single module's
domain/application core; the **public** API is read-only — the only write surface is the gated,
authenticated admin import endpoint.
