# Spec 8 — Non-functional requirements

**Realised by:** [deployment-operations](../features/deployment-operations.md).
**Constrained by:** [ADR-0004](../architecture/0004-postgresql-as-primary-datastore.md),
[ADR-0005](../architecture/0005-java-ingester-and-read-api.md),
[ADR-0010](../architecture/0010-postgresql-ctes-over-graph-db.md),
[ADR-0011](../architecture/0011-cheap-eu-vps-hosting.md),
[ADR-0016](../architecture/0016-database-schema-migrations.md) (migrations),
[ADR-0017](../architecture/0017-observability-logging-and-alerting.md) (observability),
[ADR-0019](../architecture/0019-backup-restore-and-retention.md) (backup/retention),
[ADR-0020](../architecture/0020-secrets-management.md) (secrets).

## What this describes

The constraints that shape every implementation choice, expressed as requirements.

## Cost

- **Cheap and simple.** Target a single EU VPS in the ~€7/month class
  (e.g. Hetzner CX32: 4 vCPU / 8 GB / 80 GB), hosting PostgreSQL + the Java API. No managed
  cloud datastore, no message queue, no second database engine initially. *(Off-box **cold
  object storage for backups** is not a "datastore" in this sense — it holds encrypted dumps
  only, never serves queries; see [ADR-0019](../architecture/0019-backup-restore-and-retention.md).)*
- The ingestion/parsing worker may run **locally** (off-server) — only the API and database
  must be hosted.

## Data residency

- Host within the **EU** (e.g. Germany/Finland/France) to keep personal data in-region and
  simplify GDPR ([Spec 7](07-data-protection.md)).

## Storage

- Plan for **tens of GB of PostgreSQL at most** over the full 2009→present history
  (low-single-digit millions of acts).
- The **raw fetch cache is kept** (not optional) — it is what makes ingestion resumable and
  re-parseable without re-downloading ([document-fetch](../features/document-fetch.md)). It is
  dominated by the compact per-document **XML/txt** (the happy path); PDFs are stored only when
  they were the fallback, so the cache stays modest. It lives on the VPS volume, or on EU object
  storage if it outgrows the disk. The cache is **re-derivable and is not backed up** — but a
  full re-crawl is slow under the ≤1–2 req/s budget, so treat cache loss as a multi-day
  recovery, not instant ([ADR-0019](../architecture/0019-backup-restore-and-retention.md)).
- Comfortably within a 40–80 GB VPS disk.

## Performance

- **Latency target (the home of the "~1 s" figure):** default link queries (shared admin /
  shared address / bounded multi-hop) should return within **~1 s p95** via indexed joins and
  recursive CTEs. This is the threshold ADR-0010 and [link-queries](../features/link-queries.md)
  reference; it is owned here. **Escalate to Apache AGE only** if a link query needs >3 unbounded
  hops or routinely exceeds that bound / blows `work_mem`
  ([ADR-0010](../architecture/0010-postgresql-ctes-over-graph-db.md)). Escalation is a
  *measured* decision, so query latency and `work_mem` pressure are tracked
  ([ADR-0017](../architecture/0017-observability-logging-and-alerting.md)).
- **Memory budget:** on the shared 8 GB box, PostgreSQL (`shared_buffers`, `work_mem`) and the
  JVM heap must be sized **together** so the GIN/trigram working set and the API process coexist
  without swapping — a tuning task, not just a sizing assumption.
- The historical backfill is run over several days at ≤1–2 req/s to the BOE
  ([ADR-0018](../architecture/0018-boe-source-politeness-and-retry.md)); it is not
  latency-sensitive. Daily incremental is trivial (a few dozen documents).

## Technology constraints

- **Datastore:** PostgreSQL 18, with `pg_trgm`, `fuzzystrmatch` and `unaccent` extensions.
- **Ingestion/parsing:** Java 25 (regex over XML + ported dictionaries), run locally.
- **API:** Java 25 + Micronaut, the only mandatory server-hosted component.
- **Reproducibility:** components run in Docker; schema changes via Flyway migrations
  ([ADR-0016](../architecture/0016-database-schema-migrations.md)); nightly **encrypted**
  `pg_dump` to EU cold object storage with a **tested restore** and a retention window
  ([ADR-0019](../architecture/0019-backup-restore-and-retention.md)).
- **Licensing:** GPLv3-compatible — bormeparser dictionaries are reused (see
  [ADR-0012](../architecture/0012-reuse-bormeparser-dictionaries-gpl.md)).

## Configuration & security

- **Cloud-native / 12-factor config:** all runtime configuration comes from the
  **environment** (env vars, Micronaut environments `dev`/`prod`); the **same artifact** runs
  everywhere — no per-environment builds. Secrets (DB credentials, backup-storage credentials,
  API keys) are **injected at run time** and never committed or baked into images; the concrete
  mechanism is [ADR-0020](../architecture/0020-secrets-management.md).
- **API access control:** in production the read API requires an **API key** (header
  `X-API-Key`); local development runs anonymously. Auth is enabled by default and
  **fail-closed**; accepted keys come from an env var / secret. See
  [ADR-0013](../architecture/0013-api-key-auth-and-config.md) and [Spec 6](06-public-api.md).
