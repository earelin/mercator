# Mercator — Server

The **Java 25 + Micronaut** application and the only mandatory **server-hosted** component,
stateless over PostgreSQL 18. It does two things:

1. Exposes the **read-only** public API (company search/detail, person detail, link queries).
   External consumers — including the sibling public-contracts project — are read-only.
2. Runs the **daily incremental** ingest as a Micronaut `@Scheduled` job, in-process, calling
   the [`shared`](../shared/) `IngestionService` (no HTTP ingest endpoint).

There is **no write/ingest endpoint** on the HTTP surface. Entity resolution is invoked
through the shared `IngestionService` — the same code the [`ingester`](../ingester/) backfill
uses, so both paths behave identically (ADR-0007).

In **production** the read API requires an **API key** (`X-API-Key`); in **local
development** it runs anonymously. Auth is environment-configured, enabled by default and
fail-closed, with keys injected as a runtime secret (ADR-0013). Config is 12-factor (env vars
and Micronaut environments), with one artifact for all environments.

## Responsibilities (see `docs/`)

| Area | Spec | Feature |
|------|------|---------|
| Read API (search/detail) | [Spec 6](../docs/specs/06-public-api.md) | [read-api](../docs/features/read-api.md) |
| Link queries | [Spec 5](../docs/specs/05-link-detection.md) | [link-queries](../docs/features/link-queries.md) |
| Daily incremental (scheduler) | [Spec 2](../docs/specs/02-ingestion.md) | [daily-incremental](../docs/features/daily-incremental.md) |

Depends on [`shared`](../shared/).

Governing decisions: [ADR-0005 — Three Java modules](../docs/architecture/0005-java-ingester-and-read-api.md),
[ADR-0006 — Write paths](../docs/architecture/0006-hybrid-write-path.md),
[ADR-0011 — Cheap EU VPS hosting](../docs/architecture/0011-cheap-eu-vps-hosting.md),
[ADR-0013 — API key auth, configured per environment](../docs/architecture/0013-api-key-auth-and-config.md).

> No code yet — the project is in its initial (documentation) phase. See [`docs/`](../docs/).
