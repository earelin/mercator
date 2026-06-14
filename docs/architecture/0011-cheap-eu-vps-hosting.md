# ADR-0011 — Single cheap EU VPS for hosting

## Status

Accepted.

## Context

The project must be **cheap and simple**, cannot afford commercial BORME-API pricing, and
processes personal data that benefits from EU residency ([Spec 7](../specs/07-data-protection.md)).
The corpus is small (tens of GB of text). The hosted footprint is just PostgreSQL + the Java
read API; the ingestion worker can run locally.

## Decision

Host on a **single EU virtual private server** in the ~€7/month class — baseline a Hetzner
**CX32** (4 vCPU / 8 GB / 80 GB), located in Germany/Finland (OVH France is an alternative).
Run PostgreSQL and the Java API on it (in Docker), with nightly `pg_dump` shipped off-box for
backup (see [ADR-0019](0019-backup-restore-and-retention.md) for the backup target,
encryption and retention). Resize only if measured load requires it.

## Consequences

- Very low running cost; EU residency simplifies GDPR.
- A single host is a single point of failure — mitigated by nightly off-host backups and the
  fact that the authoritative source (the BOE) can always be re-ingested.
- CX32's 8 GB gives headroom for `pg_trgm` GIN indexes plus the JVM alongside PostgreSQL.
  Sizing band: a smaller **CX22** (4 GB) may suffice for a lean steady-state API+DB *if* the
  footprint proves small, **CX42** if growth demands — but CX32 is the chosen baseline so the
  bulk load and indexing have room.
- The off-box backup target is **cold object storage, not a second live datastore** — it does
  not contradict the single-PostgreSQL-datastore decision ([ADR-0004](0004-postgresql-as-primary-datastore.md));
  details in [ADR-0019](0019-backup-restore-and-retention.md).
- VPS pricing changes over time — confirm current rates at order time.

## Alternatives considered

- **Managed cloud database / PaaS** — higher cost, conflicts with the budget; rejected.
- **Commercial BORME data API** — the cost the project explicitly cannot pay; rejected.
- **Self-hosted on-prem hardware** — more ops burden and weaker availability than a cheap
  VPS; not pursued initially.
