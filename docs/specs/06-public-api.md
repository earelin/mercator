# Spec 6 — Public API

**Realised by:** [read-api](../features/read-api.md), [link-queries](../features/link-queries.md).
**Constrained by:** [ADR-0005](../architecture/0005-java-ingester-and-read-api.md),
[ADR-0006](../architecture/0006-hybrid-write-path.md),
[ADR-0013](../architecture/0013-api-key-auth-and-config.md).

## What this describes

The HTTP surface Mercator exposes. The **public** surface is **read-only**: no public endpoint
mutates data. Ingestion runs inside the server — the daily incremental on the Micronaut
scheduler, and the historical backfill on demand via a **gated, authenticated admin import
endpoint** (off the public namespace; see below) — both writing to PostgreSQL directly
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

- **Read-only for all consumers**, including the contracts project. No *public* endpoint mutates
  data; the only write surface is the admin import endpoint below.
- Stateless; every response derives from PostgreSQL.
- **Versioned** under `/api/v1`; a breaking change ships a new version prefix.
- Returns confidence/match metadata wherever identity is probabilistic, so consumers never
  mistake a candidate for a fact. Link results expose **identity confidence and temporal
  relevance as two separate fields** — never one blended score (see
  [Spec 5 § Confidence](05-link-detection.md)).
- **Paginated** collection responses with a default and a hard maximum page size; any
  truncation (including a bounded multi-hop traversal) is signalled, never silent.
- **Rate-limited per API key**, protecting the single VPS ([Spec 8](08-non-functional.md)) from
  expensive fuzzy/graph queries; over-budget requests get `429` with `Retry-After`.
- **Consistent error model:** a JSON problem shape with `400` (bad params), `401`
  (missing/invalid key), `404` (unknown id), `429` (rate-limited) and `5xx`.
- **Suppressed personal data is filtered out entirely** (treated as not-present, not a redacted
  stub) from search, detail and link results ([Spec 7](07-data-protection.md)).

## Access control

- **In production the API is not anonymous:** every request must carry a valid **API key**
  (header `X-API-Key`); missing/invalid keys get `401 Unauthorized`. The liveness/readiness
  probe paths are the only unauthenticated endpoints, and they expose only up/down — no data or
  internal state.
- **In local development authentication is disabled** — no key is needed.
- The behaviour is selected by **environment configuration** (not by build), enabled by
  default and **fail-closed**; accepted keys come from an environment variable / injected
  secret. See [ADR-0013](../architecture/0013-api-key-auth-and-config.md).
- Consumers (including the contracts project) must hold and send a key in production.

## Admin import endpoint (gated write surface)

A single **admin** endpoint triggers the historical / massive import
([Spec 2](02-ingestion.md), [historical-backfill](../features/historical-backfill.md)). It is
**not part of the public read contract** and is held to a stricter posture than the read API:

- **Off the public namespace** — under an admin path (e.g. `/admin/imports`), separate from
  `/api/v1`.
- **By date or by month** — `POST …/by-date` and `POST …/by-month` start an import; **invalid
  bounds** return `400`.
- **Asynchronous** — a request is accepted and returns **`202`** with a **job id** and a status
  URL; the import runs in the background. `GET …/{jobId}` reports the job's status
  (`ACCEPTED` / `RUNNING` / `COMPLETED` / `FAILED`); an unknown id returns `404`.
- **Disable-able by config** — gated by `mercator.imports.historical.enabled` (**default off**).
  When disabled the routes are **absent** (`404`), not merely forbidden.
- **Always authenticated** — requires the `X-API-Key` in **every** environment, including local
  dev where reads are anonymous ([ADR-0013](../architecture/0013-api-key-auth-and-config.md)).
  Running an import therefore needs **both** the enable flag and a valid key.
