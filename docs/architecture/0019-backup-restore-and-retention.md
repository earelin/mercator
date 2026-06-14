# ADR-0019 — Tested backup/restore and retention policy

## Status

Accepted.

## Context

The system runs on a **single cheap EU VPS** ([ADR-0011](0011-cheap-eu-vps-hosting.md)) with
PostgreSQL as the only datastore ([ADR-0004](0004-postgresql-as-primary-datastore.md)). A
single host is a single point of failure, so far mitigated only by a one-line promise of
"nightly `pg_dump` to object storage" ([Spec 8](../specs/08-non-functional.md)). That
under-specifies the policy and leaves two open questions that this ADR resolves.

First, [Spec 8](../specs/08-non-functional.md) also says **"no managed cloud datastore"**.
Shipping dumps to object storage must not be read as reintroducing a second live datastore
through the back door: backup storage is **cold, write-only-from-the-app, never queried by the
running system**. It is also a place where personal data leaves the VPS, so under
[Spec 7](../specs/07-data-protection.md) it must stay in the **EU** and be **encrypted at
rest** with controlled access — a `pg_dump` of this database contains administrators' and
attorneys' names (and possibly residual identifiers before suppression runs).

Second, an **untested backup is not a backup**. A retention window must also respect GDPR
**storage limitation** ([Spec 7](../specs/07-data-protection.md)): we keep copies only as long
as recovery genuinely needs them, no longer.

## Decision

- **Nightly `pg_dump --format=custom`** of the single database, run on the VPS. Custom format
  is compressed and supports selective/parallel `pg_restore`.
- The dump is **encrypted before it leaves the host** (`age` with a recipient key, or `gpg`),
  *or* relies on provider server-side encryption (SSE) — encryption is mandatory, the
  mechanism is an operator choice. The decryption key is **not** stored on the VPS.
- It is then shipped to **off-box object storage in an EU region** — e.g. Hetzner Storage Box
  (DE/FI), OVH Object Storage (FR), or Backblaze B2 EU — over an authenticated, access-scoped
  credential. This is **cold backup only, not a second datastore**.
- **RPO ≈ 24 h** (one nightly dump; a same-day crash loses at most one day's incremental,
  which is re-ingestible from the BOE). **RTO: hours** — provision a VPS, restore the latest
  dump, re-run Flyway is not needed (the dump carries the schema). Target a few hours, not
  minutes; this is hobby-scale.
- **Retention: 7 daily + 4 weekly** copies, older ones pruned automatically. The window is
  bounded by GDPR storage-limitation, not extended "just in case".
- A **documented restore runbook** lives in the repo and is **exercised periodically** (e.g.
  quarterly: pull latest, decrypt, `pg_restore` to a throwaway DB, sanity-check row counts).
  An untested backup does not count as a backup.
- The **raw-document fetch cache is NOT backed up.** It is re-derivable by re-crawling the
  BOE, so it is excluded to keep dumps small.

## Consequences

- Recovery from total VPS loss is a documented, rehearsed procedure, not an improvised one.
- Personal data at rest off-box is encrypted and EU-resident, consistent with
  [Spec 7](../specs/07-data-protection.md), and the "no managed datastore" constraint of
  [Spec 8](../specs/08-non-functional.md) is preserved (cold storage ≠ live store).
- The dump is small because the fetch cache is excluded — **but** rebuilding that cache after a
  bare-metal restore means re-crawling, which is **slow under the BOE rate limit**
  ([ADR-0018](0018-boe-source-politeness-and-retry.md)). The database itself restores in hours;
  a fully warm cache may take much longer. Steady-state queries do not need the cache, so this
  does not gate RTO for serving — only for re-ingestion throughput.
- Periodic restore tests cost a little operator time and a throwaway VPS hour, accepted as the
  price of trustworthy backups.
- Key management is now a real responsibility: lose the decryption key and the backups are
  useless. The key is held off-VPS by the maintainer.

## Alternatives considered

- **Continuous archiving / PITR (WAL shipping)** — gives near-zero RPO but adds standing
  complexity and storage churn disproportionate to a hobby single-VPS project; the 24 h RPO is
  acceptable because the BOE is re-ingestible. Rejected for now.
- **Managed/automated cloud-database backups** — implies a managed datastore, which both
  [ADR-0011](0011-cheap-eu-vps-hosting.md) and [Spec 8](../specs/08-non-functional.md) rule
  out on cost and principle. Rejected.
- **Plain `pg_dumpall` to local disk only** — no off-host copy means a disk or host loss is
  total data loss; defeats the purpose. Rejected.
- **Backing up the fetch cache too** — bloats every dump for data that is freely
  re-derivable; excluded deliberately. Rejected.
