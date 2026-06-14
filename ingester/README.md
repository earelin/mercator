# Mercator — Ingester

The **Java offline CLI** (Java 25, same stack as [`server`](../server/)) that performs the
**historical backfill** (2009→present). Runs **locally / off-server** — it is not hosted on
the API VPS. It enumerates the BORME summaries over a date range, fetches each Sección A
document as structured XML, parses the acts (regex + ported bormeparser dictionaries),
normalises the records, and **writes directly to PostgreSQL** via bulk staging + an SQL merge.

It calls the same [`shared`](../shared/) `IngestionService` and resolution functions the
server's daily job uses, so both write paths behave identically (ADR-0007). The parser
**never resolves identity**.

> The **daily incremental** is *not* part of the ingester — it runs inside the
> [`server`](../server/) on the Micronaut scheduler ([ADR-0006](../docs/architecture/0006-hybrid-write-path.md)).

## Responsibilities (see `docs/`)

| Area | Spec | Feature |
|------|------|---------|
| Enumerate summaries | [Spec 1](../docs/specs/01-data-sources.md) | [summary-enumeration](../docs/features/summary-enumeration.md) |
| Fetch documents (XML→txt→PDF) | [Spec 1](../docs/specs/01-data-sources.md) | [document-fetch](../docs/features/document-fetch.md) |
| Parse acts | [Spec 3](../docs/specs/03-extraction.md) | [act-parsing](../docs/features/act-parsing.md) |
| Normalise records | [Spec 3](../docs/specs/03-extraction.md), [Spec 4](../docs/specs/04-data-model.md) | [entity-extraction-normalisation](../docs/features/entity-extraction-normalisation.md) |
| Historical backfill | [Spec 2](../docs/specs/02-ingestion.md) | [historical-backfill](../docs/features/historical-backfill.md) |

> The enumerate/fetch/parse/normalise logic lives in [`shared`](../shared/); the ingester is
> the offline driver around it for the backfill.

Depends on [`shared`](../shared/).

Governing decision: [ADR-0005 — Three Java modules](../docs/architecture/0005-java-ingester-and-read-api.md).

> No code yet — the project is in its initial (documentation) phase. See [`docs/`](../docs/).
