# ADR-0018 — BOE source politeness and fail-soft retry policy

## Status

Accepted.

## Context

Mercator fetches everything from a single free public source: the BOE. Two access patterns
exist — the daily summary via the `datosabiertos` REST API ([ADR-0003](0003-datosabiertos-rest-api-over-legacy-xml.md))
and per-document fetches (XML → `txt.php` → PDF fallback, [ADR-0002](0002-structured-xml-over-pdf-parsing.md)).
The **historical backfill** (run in-server, [ADR-0005](0005-java-ingester-and-read-api.md)) enumerates
years of summaries and pulls **millions of documents** against an endpoint with **no commercial
SLA**. The BOE explicitly describes these feeds as *informative, not guaranteed*, so we must be
a courteous client and never assume availability.

Without an explicit policy, naive concurrency would hammer the BOE, transient `429`/`5xx`
responses would surface as crashes mid-backfill, and we would have no way for the BOE operator
to contact us if our traffic became a problem. The non-functional spec already caps us at a low
request rate; this ADR makes that operational.

## Decision

Adopt a shared politeness + resilience policy for **all** BOE HTTP access:

1. **One global token-bucket rate limiter** capped at **~1–2 requests/second**, shared across
   summary-enumeration *and* document-fetch. Every outbound request acquires a token first, so
   no amount of internal concurrency (threads, parallel days) can exceed the global ceiling.
2. **Descriptive `User-Agent`** identifying the project and a contact URL/email, e.g.
   `Mercator/<version> (+https://…; mailto:…)`, so the BOE operator can reach the operator.
3. **Retry with exponential backoff + jitter** on `429` and `5xx`, with a **bounded
   max-attempts**. On give-up, classify the failure (retryable vs permanent) and record a
   `borme_log` **ERROR** for that document/day rather than crashing the run. A `404` is *not*
   retried (non-publication day / absent document — skip per [ADR-0003](0003-datosabiertos-rest-api-over-legacy-xml.md)).
4. **Respect `Retry-After`** when present (it overrides the computed backoff delay).
5. **Honour the BOE `robots.txt`** for the paths we use. The `datosabiertos/api/…`,
   `xml.php`, `txt.php` and PDF paths **must be confirmed permitted** before bulk fetching
   (see [Spec 7](../specs/07-data-protection.md)); if a path is disallowed we do not crawl it.

The backfill runs **in-server, off the request path** ([ADR-0005](0005-java-ingester-and-read-api.md))
and can be throttled further or scheduled overnight to flatten load. **Fail-soft is mandatory**: a
single failed fetch degrades to a logged, re-runnable gap — never a process abort.

## Consequences

- The BOE sees a steady, identifiable, low-rate client; our traffic stays well within polite
  limits and is attributable to a reachable operator.
- The backfill is **resumable**: documents that ultimately fail land as `borme_log` ERRORs and
  can be re-attempted on a later pass; idempotency ([ADR-0006](0006-hybrid-write-path.md)) makes
  re-runs safe no-ops.
- The rate cap makes a full historical backfill inherently **slow** (hours/overnight) — an
  accepted cost of being a good citizen of a free source.
- A shared limiter is a single contention point in the fetch layer; it lives behind the BORME
  source port ([ADR-0014](0014-hexagonal-architecture.md)) so the core stays I/O-agnostic.
- `robots.txt` confirmation is a hard prerequisite gating the bulk fetch (tracked in [Spec 7](../specs/07-data-protection.md)).

## Alternatives considered

- **No global limiter (per-task rate limits only)** — concurrent tasks would multiply the
  effective rate past the cap; rejected.
- **Crash-on-error / no retry** — a single transient `5xx` aborts a multi-hour backfill;
  rejected as fragile against an explicitly best-effort source.
- **Unbounded/aggressive retry** — risks amplifying load during a BOE outage and could look
  like abuse; rejected in favour of bounded attempts plus `Retry-After`.
- **Generic or absent `User-Agent`** — leaves the operator unreachable and the traffic
  unattributable; rejected.
- **Run the backfill on the production VPS at full speed** — competes with the read API for the
  cheap host's resources and offers no politeness benefit; rejected in favour of off-server,
  throttled runs.
