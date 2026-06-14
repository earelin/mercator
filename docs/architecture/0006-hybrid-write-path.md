# ADR-0006 — Two write paths, both direct to the DB via the shared library

## Status

Proposed (pending approval). *(Updated: daily incremental moved in-server; HTTP ingest API
dropped — see Decision.)*

## Context

There are two ingestion workloads with very different volumes: a **one-time historical
backfill** (2009→present, millions of acts) and an **ongoing daily incremental** (a few
dozen documents/day). The contracts project is a read-only consumer, so the BORME pipeline
is the *only writer* — the design tension is bulk-load speed vs. consistency of ongoing
resolution, not concurrent writers.

Both workloads share the same fetch/parse/normalise/resolve/persist logic, which lives in the
**`shared` library** ([ADR-0005](0005-java-ingester-and-read-api.md)). Because the daily
incremental runs **inside the server** (Micronaut `@Scheduled`), it can call that logic
directly — there is no need to hand documents to the server over HTTP.

## Decision

Use **two write paths, both writing directly to PostgreSQL through the shared
`IngestionService`** — there is **no HTTP ingest API**:

- **Historical backfill (offline `ingester`) → bulk staging + merge.** Parsed rows are
  bulk-`COPY`ed into staging tables, then a single SQL merge step resolves and upserts them
  into the live tables. Optimised for millions of rows.
- **Daily incremental (`server`, Micronaut `@Scheduled`) → in-process upsert.** The tiny
  daily volume is resolved and upserted row-by-row, in the hosted server process.

Both paths invoke the **same** entity-resolution logic via `shared`
([ADR-0007](0007-single-source-of-truth-entity-resolution.md)).

## Consequences

- Backfill is fast (bulk `COPY` + one merge), resumable via `borme_log`, idempotent via the
  `borme_act` UNIQUE constraint.
- The daily incremental needs no extra process, external scheduler, or HTTP endpoint; the
  public API stays **read-only**.
- Idempotency is enforced at two layers in `shared`: app-level (`borme_log` short-circuit)
  and DB-level (`UNIQUE … ON CONFLICT DO NOTHING`).
- Two code paths to keep behaviourally consistent — addressed by sharing the
  `IngestionService` and the single-resolution-source rule.

## Alternatives considered

- **Daily via an HTTP ingest API** — the only caller would be our own in-server scheduler;
  an HTTP hop to ourselves adds a moving part for no benefit; rejected (the endpoint is
  removed).
- **Everything through one bulk path** — row-by-row daily reuses the simple upsert; bulk
  staging is only worth it for the millions-of-rows backfill; keep both.
- **Duplicating the logic in each module** — would risk divergence; avoided by the `shared`
  library.
