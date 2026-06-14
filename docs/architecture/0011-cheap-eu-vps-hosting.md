# ADR-0011 — Single cheap EU VPS for hosting

## Status

Accepted.

## Context

The project must be **cheap and simple**, cannot afford commercial BORME-API pricing, and
processes personal data that benefits from EU residency ([Spec 7](../specs/07-data-protection.md)).
The corpus is small (tens of GB of text). The hosted footprint is just PostgreSQL + the Java
read API; the ingestion worker can run locally.

## Decision

Host on a **single EU virtual private server** in the ~€7/month class — e.g. a Hetzner
CX32 (4 vCPU / 8 GB / 80 GB), located in Germany/Finland (OVH France is an alternative).
Run PostgreSQL and the Java API on it (in Docker), with nightly `pg_dump` to object storage.
Scale up to a larger instance only if measured load requires it.

## Consequences

- Very low running cost; EU residency simplifies GDPR.
- A single host is a single point of failure — mitigated by nightly off-host backups and the
  fact that the authoritative source (the BOE) can always be re-ingested.
- Headroom for `pg_trgm` GIN indexes and the bulk load; CX22 is sufficient for a lean
  API+DB, CX42 if growth demands.
- VPS pricing changes over time — confirm current rates at order time.

## Alternatives considered

- **Managed cloud database / PaaS** — higher cost, conflicts with the budget; rejected.
- **Commercial BORME data API** — the cost the project explicitly cannot pay; rejected.
- **Self-hosted on-prem hardware** — more ops burden and weaker availability than a cheap
  VPS; not pursued initially.
