# Mercator — Shared

The **common Java library** depended on by both [`server`](../server/) and
[`ingester`](../ingester/), to keep ingestion logic DRY and guarantee both write paths behave
identically. It is the home of everything that is **not** specific to "hosted API" or
"offline CLI".

## Contains

- **BOE client** — summary enumeration (`datosabiertos` REST) and per-document fetch
  (XML → `txt.php` → PDF fallback) with caching, rate-limiting and backoff.
- **Parser** — XML `<texto>` → company blocks → acts, using the ported bormeparser
  dictionaries (act vocabulary, cargo roles, province codes).
- **Normalisation** — the single canonical `norm_name`/registry-coordinate/role logic.
- **Domain model / DTOs** — companies, persons, addresses, acts, appointments.
- **Persistence + resolution invocation** — the `IngestionService` that calls the shared
  `resolve_company`/`resolve_person` DB functions, upserts, and enforces idempotency
  (`borme_log` + UNIQUE constraints).

Both write paths call this one `IngestionService`:

- `server` — the daily incremental, on the Micronaut scheduler (in-process).
- `ingester` — the historical backfill (offline, bulk staging + merge).

## Governing decisions

- [ADR-0005 — Offline Java ingester + hosted Java read API](../docs/architecture/0005-java-ingester-and-read-api.md) (three Gradle modules)
- [ADR-0006 — Write paths](../docs/architecture/0006-hybrid-write-path.md)
- [ADR-0007 — Single source of truth for entity resolution](../docs/architecture/0007-single-source-of-truth-entity-resolution.md)

> No code yet — the project is in its initial (documentation) phase. See [`docs/`](../docs/).
