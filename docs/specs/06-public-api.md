# Spec 6 — Public API

**Realised by:** [read-api](../features/read-api.md), [link-queries](../features/link-queries.md).
**Constrained by:** [ADR-0005](../architecture/0005-java-ingester-and-read-api.md),
[ADR-0006](../architecture/0006-hybrid-write-path.md).

## What this describes

The HTTP surface Mercator exposes. It is **read-only**: there is no write/ingest endpoint.
Ingestion happens off the HTTP surface — the historical backfill runs in the offline
`ingester`, and the daily incremental runs inside the `server` on the Micronaut scheduler,
both writing to PostgreSQL via the shared library
([Spec 2](02-ingestion.md), [ADR-0006](../architecture/0006-hybrid-write-path.md)).

## Read API (public, read-only)

A stateless Java 25 / Micronaut service over PostgreSQL. Capabilities:

- **Company search** — by name (fuzzy, normalised) and province; returns ranked matches.
- **Company detail** — a company with its acts, current and historical administrators, and
  current/historical registered addresses.
- **Person detail** — a person with the companies they are/were associated with and in what
  roles, presented with confidence where identity is probabilistic.
- **Link queries** — shared administrators, shared address, and bounded multi-hop
  connections between companies (see [Spec 5](05-link-detection.md)).

Properties:

- **Read-only for all consumers**, including the contracts project. No HTTP endpoint mutates
  data.
- Stateless; every response derives from PostgreSQL.
- Returns confidence/match metadata wherever identity is probabilistic, so consumers never
  mistake a candidate for a fact.
