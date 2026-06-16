# Feature — Historical backfill

## Summary

A resumable, idempotent, rate-limited crawl of 2009→present that bulk-loads parsed acts into
staging and merges them into the live model via the shared resolution functions. It is triggered
on demand through a **gated, authenticated admin import endpoint** and runs **asynchronously
inside the server**.

## Related specs / ADRs

- Specs: [2 — Ingestion](../specs/02-ingestion.md), [6 — Public API](../specs/06-public-api.md)
- ADRs: [0005 — Single Micronaut module](../architecture/0005-java-ingester-and-read-api.md), [0006 — Hybrid write path](../architecture/0006-hybrid-write-path.md), [0013 — API key auth & config](../architecture/0013-api-key-auth-and-config.md), [0018 — BOE source politeness & retry](../architecture/0018-boe-source-politeness-and-retry.md)

## Functional behaviour

- **Triggered via the admin import endpoint** (off the public namespace; see
  [read-api](read-api.md) and [Spec 6](../specs/06-public-api.md)):
  - `POST /admin/imports/by-date` — import every Sección A document published on one day.
  - `POST /admin/imports/by-month` (`YYYY-MM`) — import a single calendar month (equivalent to
    that month's start/end bounds) — convenient for **initial testing** so a small,
    representative slice can be ingested without a full multi-day backfill.
  - A request is **accepted asynchronously**: it returns `202` with a **job id** and a status
    URL; `GET /admin/imports/{jobId}` reports `ACCEPTED`/`RUNNING`/`COMPLETED`/`FAILED`.
  - The endpoint is **disabled by default** (`mercator.imports.historical.enabled`) and
    **always requires the API key** ([ADR-0013](../architecture/0013-api-key-auth-and-config.md)).
- For each Sección A document in the requested span: fetch ([document-fetch](document-fetch.md)),
  parse ([act-parsing](act-parsing.md)), normalise
  ([entity-extraction-normalisation](entity-extraction-normalisation.md)), and bulk-`COPY`
  rows into `staging_act`.
- Run a **merge step** through the in-server ingestion service's bulk-merge entry point (the
  *same* resolution logic the daily path uses, [entity-resolution](entity-resolution.md), just
  driven set-at-a-time): for each unprocessed staging row it invokes `resolve_company`/
  `resolve_person`/`resolve_address` and upserts into the live tables with `ON CONFLICT DO
  NOTHING`; marks the row `processed`; writes `borme_log`. The backfill does **not** define its
  own resolution — it calls the shared core, exactly like the daily incremental.
- **Batch** the merge in chunks (e.g. 5–10k rows) to bound transaction size.
- **Errata reconciliation pass (ordering).** Because a *Fe de erratas* can only be applied once
  its target act is in the live tables, corrections are **not** applied inline during the main
  merge. After the act merge of a span completes, a dedicated pass re-attempts every
  `act_correction` with `status = UNAPPLIED` (their targets may now be present); matches are
  applied, the rest stay `UNAPPLIED` for the next pass. The pass is idempotent (applied-marker
  guard) and re-runs safely on resume, so cross-batch or out-of-order publication never loses a
  correction. See [errata-corrections](errata-corrections.md).
- **Resumable** via `borme_log` (skip already-MERGED documents); **idempotent** via the
  `borme_act` UNIQUE constraint — so a re-triggered import after an interruption is a no-op for
  already-processed documents. **Rate-limited** (≤1–2 req/s), runnable over several days.
- Runs **in-process in the server** ([ADR-0005](../architecture/0005-java-ingester-and-read-api.md)),
  writing directly to PostgreSQL — off the public request path.

## Data flow

```mermaid
flowchart LR
    REQ["POST /admin/imports/by-date|by-month<br/>→ 202 + jobId"] --> E["enumerate"] --> F["fetch"] --> P["parse"] --> N["normalise"] --> C["COPY → staging_act"]
    C --> M["merge (resolve_* + upsert)"] --> L["live tables<br/>borme_log = MERGED"]
```

## Inputs / outputs

- **Input:** the import request — a single date or a `YYYY-MM` month; rate-limit and batch-size
  config; DB connection. The endpoint must be enabled by config and the request authenticated.
- **Output:** `202` + a job id (status pollable); populated live tables; `borme_log` reflecting
  per-document status; cached raw documents.

## Edge cases

- **Interruption / server restart** — re-trigger the import; `borme_log` resumes; no
  double-insertion. (In-flight job *status* is in-memory and lost on restart; the data is not.)
- **Per-document parse error** — recorded as `ERROR` with detail; does not abort the crawl;
  retryable independently.
- **Huge transactions** — avoided by chunked merges.
- **Politeness** — must not hammer the BOE; spread over days.
- **Endpoint disabled / unauthenticated** — `404` when the toggle is off; `401` without a valid
  key.
- **Invalid bounds** — a malformed date/month returns `400`.

## Acceptance criteria

- A full backfill (run as a sequence of by-month/by-date imports) completes and is re-runnable
  with no duplicates.
- Killing and restarting the server mid-run, then re-triggering, continues correctly.
- Errored documents are isolated and retryable.
- The endpoint is absent (`404`) when disabled and rejects unauthenticated requests (`401`).

## Implementation issues

- [ ] Admin import controller: `POST by-date` / `POST by-month` → `202` + job id; `GET {jobId}`
      status; `400` on invalid bounds. *(Scaffolded.)*
- [ ] Config toggle `mercator.imports.historical.enabled` (default off); `@Requires`-gated
      controller so the routes are absent when disabled. *(Scaffolded.)*
- [ ] Always-on API-key auth on the endpoint, including local dev
      ([ADR-0013](../architecture/0013-api-key-auth-and-config.md)).
- [ ] Async job runner on a dedicated executor + job-status store (one active import at a time).
      *(In-memory store scaffolded; runner pending.)*
- [ ] Backfill driver: date iteration + work queue over enumeration/fetch/parse.
- [ ] `COPY`-based bulk loader into `staging_act`.
- [ ] Merge step via the ingestion service's bulk entry point (built in
      [entity-resolution](entity-resolution.md)) + chunked upserts + `borme_log` updates.
- [ ] Post-merge errata reconciliation pass over `UNAPPLIED` `act_correction` rows (idempotent).
- [ ] Resume logic from `borme_log`; per-document error isolation + retry.
- [ ] Rate-limit/backoff config shared with document-fetch.
- [ ] Progress/metrics reporting for long runs (surfaced through the job status).
