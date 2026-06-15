# Feature — Historical backfill

## Summary

A one-time, resumable, idempotent, rate-limited crawl of 2009→present that bulk-loads parsed
acts into staging and merges them into the live model via the shared resolution functions.

## Related specs / ADRs

- Specs: [2 — Ingestion](../specs/02-ingestion.md)
- ADRs: [0006 — Hybrid write path](../architecture/0006-hybrid-write-path.md), [0018 — BOE source politeness & retry](../architecture/0018-boe-source-politeness-and-retry.md)

## Functional behaviour

- Iterate dates 2009-01-02 → today via [summary-enumeration](summary-enumeration.md).
- **Configurable date range.** The crawl span is bounded by CLI parameters, defaulting to
  the full 2009-01-02 → today range. A `--month YYYY-MM` shorthand restricts the run to a
  single calendar month (equivalent to setting start/end to that month's bounds) — intended
  for **initial testing** so a small, representative slice can be ingested without a full
  multi-day backfill. Bounded runs remain resumable and idempotent like a full run.
- For each Sección A document: fetch ([document-fetch](document-fetch.md)), parse
  ([act-parsing](act-parsing.md)), normalise
  ([entity-extraction-normalisation](entity-extraction-normalisation.md)), and bulk-`COPY`
  rows into `staging_act`.
- Run a **merge step** through the shared `IngestionService`'s bulk-merge entry point (the
  *same* resolution logic the daily path uses, [entity-resolution](entity-resolution.md), just
  driven set-at-a-time): for each unprocessed staging row it invokes `resolve_company`/
  `resolve_person`/`resolve_address` and upserts into the live tables with `ON CONFLICT DO
  NOTHING`; marks the row `processed`; writes `borme_log`. The backfill does **not** define its
  own resolution — it calls `shared`, exactly like the daily incremental.
- **Batch** the merge in chunks (e.g. 5–10k rows) to bound transaction size.
- **Errata reconciliation pass (ordering).** Because a *Fe de erratas* can only be applied once
  its target act is in the live tables, corrections are **not** applied inline during the main
  merge. After the act merge of a span completes, a dedicated pass re-attempts every
  `act_correction` with `status = UNAPPLIED` (their targets may now be present); matches are
  applied, the rest stay `UNAPPLIED` for the next pass. The pass is idempotent (applied-marker
  guard) and re-runs safely on resume, so cross-batch or out-of-order publication never loses a
  correction. See [errata-corrections](errata-corrections.md).
- **Resumable** via `borme_log` (skip already-MERGED documents); **idempotent** via the
  `borme_act` UNIQUE constraint. **Rate-limited** (≤1–2 req/s), runnable over several days.
- Runs **locally/off-server** ([ADR-0005](../architecture/0005-java-ingester-and-read-api.md)),
  writing directly to PostgreSQL.

## Data flow

```mermaid
flowchart LR
    D["dates"] --> E["enumerate"] --> F["fetch"] --> P["parse"] --> N["normalise"] --> C["COPY → staging_act"]
    C --> M["merge (resolve_* + upsert)"] --> L["live tables<br/>borme_log = MERGED"]
```

## Inputs / outputs

- **Input:** date-range bounds — explicit start/end dates or a `--month YYYY-MM` shorthand
  (defaults to the full 2009→today range); rate-limit and batch-size config; DB connection.
- **Output:** populated live tables; `borme_log` reflecting per-document status; cached raw
  documents.

## Edge cases

- **Interruption** — restart resumes from `borme_log`; no double-insertion.
- **Per-document parse error** — recorded as `ERROR` with detail; does not abort the crawl;
  retryable independently.
- **Huge transactions** — avoided by chunked merges.
- **Politeness** — must not hammer the BOE; spread over days.

## Acceptance criteria

- A full backfill completes and is re-runnable with no duplicates.
- Killing and restarting mid-run continues correctly.
- Errored documents are isolated and retryable.

## Implementation issues

- [ ] Backfill driver: date iteration + work queue over enumeration/fetch/parse.
- [ ] CLI date-range parameters: explicit start/end dates + `--month YYYY-MM` shorthand for
      single-month test runs (default = full 2009→today range).
- [ ] `COPY`-based bulk loader into `staging_act`.
- [ ] Merge step via shared `IngestionService` bulk entry point (built in
      [entity-resolution](entity-resolution.md)) + chunked upserts + `borme_log` updates.
- [ ] Post-merge errata reconciliation pass over `UNAPPLIED` `act_correction` rows (idempotent).
- [ ] Resume logic from `borme_log`; per-document error isolation + retry.
- [ ] Rate-limit/backoff config shared with document-fetch.
- [ ] Progress/metrics reporting for long runs.
