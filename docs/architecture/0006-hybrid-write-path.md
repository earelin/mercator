# ADR-0006 — Two write paths, both in-server and direct to the DB

## Status

Accepted. *(Updated 2026-06: both write paths now run inside the single server module; the
historical bulk-merge path is triggered by a gated admin HTTP endpoint instead of an offline
CLI. See Decision. Maintainer-approved redesign amendment — see [ADR-0005](0005-java-ingester-and-read-api.md).)*

## Context

There are two ingestion workloads with very different volumes: a **one-time historical
backfill** (2009→present, millions of acts) and an **ongoing daily incremental** (a few
dozen documents/day). The contracts project is a read-only consumer, so the BORME pipeline
is the *only writer* — the design tension is bulk-load speed vs. consistency of ongoing
resolution, not concurrent writers.

Both workloads share the same fetch/parse/normalise/resolve/persist logic, which lives in the
**domain/application core** of the single module ([ADR-0005](0005-java-ingester-and-read-api.md)).
Because both the daily incremental and the historical import run **inside the server**, they
call that logic directly — there is no need to hand documents over HTTP between components.

## Decision

Use **two write paths, both writing directly to PostgreSQL through the in-server ingestion
service** — neither writes over a public HTTP surface:

- **Historical backfill → bulk staging + merge.** Triggered on demand via the **gated admin
  import endpoint** ([ADR-0005](0005-java-ingester-and-read-api.md)) by date or month, it runs
  **asynchronously inside the server**: parsed rows are bulk-`COPY`ed into staging tables, then a
  single SQL merge step resolves and upserts them into the live tables. Optimised for millions of
  rows.
- **Daily incremental → in-process upsert.** A Micronaut `@Scheduled` bean resolves and upserts
  the tiny daily volume row-by-row, in the same server process.

Both paths invoke the **same** entity-resolution logic via the shared core
([ADR-0007](0007-single-source-of-truth-entity-resolution.md)).

The **public** API remains read-only ([Spec 6](../specs/06-public-api.md)); the historical-import
endpoint is an **admin** surface — off the public namespace, **disabled by default**, and
**always authenticated** ([ADR-0013](0013-api-key-auth-and-config.md)) — so the one HTTP write
surface is a narrow, gated exception, not an open ingest API.

## Consequences

- Backfill is fast (bulk `COPY` + one merge), resumable via `borme_log`, idempotent via the
  `borme_act` UNIQUE constraint — and an interrupted async import is safely re-triggerable.
- The daily incremental needs no extra process or external scheduler.
- Idempotency is enforced at two layers in the core: app-level (`borme_log` short-circuit) and
  DB-level (`UNIQUE … ON CONFLICT DO NOTHING`).
- Two code paths to keep behaviourally consistent — addressed by sharing the ingestion service
  and the single-resolution-source rule.
- The admin import endpoint adds a small write surface to the hosted server; the
  config toggle + mandatory auth keep it isolated and off by default.

## Alternatives considered

- **Historical backfill as an offline CLI (prior version)** — kept all writes off the HTTP
  surface, but cost a second artifact and a local run procedure; superseded by the gated in-server
  endpoint for simplicity ([ADR-0005](0005-java-ingester-and-read-api.md)).
- **An open HTTP ingest API** — a write endpoint any consumer could call; rejected. The import
  endpoint is admin-only, gated, and authenticated — not part of the public contract.
- **Everything through one bulk path** — row-by-row daily reuses the simple upsert; bulk
  staging is only worth it for the millions-of-rows backfill; keep both.
- **Duplicating the logic per path** — would risk divergence; avoided by the shared core.
