# Spec 8 — Non-functional requirements

**Realised by:** [deployment-operations](../features/deployment-operations.md).
**Constrained by:** [ADR-0004](../architecture/0004-postgresql-as-primary-datastore.md),
[ADR-0005](../architecture/0005-java-ingester-and-read-api.md),
[ADR-0010](../architecture/0010-postgresql-ctes-over-graph-db.md),
[ADR-0011](../architecture/0011-cheap-eu-vps-hosting.md).

## What this describes

The constraints that shape every implementation choice, expressed as requirements.

## Cost

- **Cheap and simple.** Target a single EU VPS in the ~€7/month class
  (e.g. Hetzner CX32: 4 vCPU / 8 GB / 80 GB), hosting PostgreSQL + the Java API. No managed
  cloud datastore, no message queue, no second database engine initially.
- The ingestion/parsing worker may run **locally** (off-server) — only the API and database
  must be hosted.

## Data residency

- Host within the **EU** (e.g. Germany/Finland/France) to keep personal data in-region and
  simplify GDPR ([Spec 7](07-data-protection.md)).

## Storage

- Plan for **tens of GB of PostgreSQL at most** over the full 2009→present history
  (low-single-digit millions of acts). Raw text/PDF caches, if kept, add object storage or
  a larger volume. Comfortably within a 40–80 GB VPS disk.

## Performance

- Default link queries (shared admin / shared address / bounded multi-hop) should return
  quickly via indexed joins and recursive CTEs. **Escalate to Apache AGE only** if a link
  query needs >3 unbounded hops or routinely exceeds ~1 s / blows `work_mem`
  ([ADR-0010](../architecture/0010-postgresql-ctes-over-graph-db.md)).
- The historical backfill is run over several days at ≤1–2 req/s to the BOE; it is not
  latency-sensitive. Daily incremental is trivial (a few dozen documents).

## Technology constraints

- **Datastore:** PostgreSQL 18, with `pg_trgm`, `fuzzystrmatch` and `unaccent` extensions.
- **Ingestion/parsing:** Java 25 (regex over XML + ported dictionaries), run locally.
- **API:** Java 25 + Micronaut, the only mandatory server-hosted component.
- **Reproducibility:** components run in Docker; nightly `pg_dump` to object storage.
- **Licensing:** GPLv3-compatible — bormeparser dictionaries are reused (see
  [ADR-0012](../architecture/0012-reuse-bormeparser-dictionaries-gpl.md)).
