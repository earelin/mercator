# Spec 2 — Ingestion

**Realised by:** [historical-backfill](../features/historical-backfill.md),
[daily-incremental](../features/daily-incremental.md),
[summary-enumeration](../features/summary-enumeration.md),
[document-fetch](../features/document-fetch.md).
**Constrained by:** [ADR-0006](../architecture/0006-hybrid-write-path.md).

## What this describes

When and how the corpus enters Mercator, and the guarantees the ingestion process offers
regardless of implementation.

## Two ingestion modes

1. **Historical backfill** — a one-time bulk load of every publication day from
   2009-01-02 to the present. Runs in the offline `ingester`, off the server's request path,
   spread over several days to stay polite to the BOE.
2. **Daily incremental** — runs **inside the server** on the Micronaut scheduler; each
   publication day it picks up that day's (and the previous day's, to catch late publication)
   new documents.

Both modes enumerate the daily summary, fetch each Sección A document, parse it, and
persist the resulting acts.

## Observable guarantees

- **Completeness.** For any date in range, every Sección A document present in that day's
  summary is eventually fetched, parsed and stored, or recorded as an error for retry.
- **Non-publication days are skipped cleanly.** A 404 summary (weekend/holiday) is a
  normal "nothing to do", not a failure.
- **Resumability.** Ingestion can stop and restart at any point and continue from where it
  left off; already-processed documents are not re-fetched or re-parsed unnecessarily.
  Progress is tracked per document.
- **Idempotency.** Processing the same document more than once never creates duplicate
  acts or duplicate entities. Re-running a day is always safe — including *Fe de erratas*
  corrections, which apply at most once (guarded by an applied-marker) and whose target,
  published earlier, is normally already present under forward date iteration
  ([ADR-0015](../architecture/0015-auto-apply-fe-de-erratas-corrections.md)).
- **Politeness.** Requests to the BOE are rate-limited with backoff on 429/5xx, identify
  themselves with a User-Agent, and raw responses are cached so re-parsing never
  re-downloads.

## Write paths

Ingestion writes through two paths, both **directly to the database via the shared library**
(see [ADR-0006](../architecture/0006-hybrid-write-path.md)) — there is no HTTP ingest API:

- **Backfill (offline `ingester`) → bulk staging + merge.** Millions of acts are bulk-loaded
  into staging tables and merged into the live model in one resolution step.
- **Daily incremental (`server` scheduler) → in-process upsert.** The tiny daily volume is
  resolved and upserted row-by-row inside the hosted server.

Both paths invoke the **same entity-resolution logic** (see
[Spec 4](04-data-model.md) and [entity-resolution](../features/entity-resolution.md)); the
parser itself never resolves identity.

## What "processed" means

A document moves through tracked states — fetched → parsed → merged — with errors
recorded against the document id so they can be retried independently. The unit of
idempotency is the **BORME document** (`BORME-A-YYYY-NNN-PP`), which equals the unit of
the crawl.

## Volume expectations

The corpus is modest text data: low-hundreds-of-thousands of acts per year, low-single-
digit millions over the full history, tens of GB of PostgreSQL at most. Daily incremental
load is a few dozen province documents. See [Spec 8](08-non-functional.md).
