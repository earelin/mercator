# ADR-0016 — Versioned database schema migrations with Flyway

## Status

Accepted. *(Updated 2026-06: the prototype's incremental migrations (V1.1.0–V1.9.0) were
consolidated into a single `V1.0.0__baseline.sql` baseline — safe because the prototype held no
production data — and versions now follow semver `x.x.x`. Maintainer-approved redesign amendment.)*

## Context

PostgreSQL is the single datastore ([ADR-0004](0004-postgresql-as-primary-datastore.md)), and
in this project the database holds **more than tables**: entity resolution lives *in* the DB
as the `resolve_company` / `resolve_person` PL/pgSQL functions
([ADR-0007](0007-single-source-of-truth-entity-resolution.md)), alongside the schema, the
`pg_trgm` GIN/trigram indexes, and the `ON CONFLICT` unique constraints that enforce
idempotency. All of this is the riskiest, most load-bearing logic in the system, and it must
be **versioned and deployed atomically with the application code** that calls it — a function
signature and its Java caller cannot drift.

Both write paths depend on the same schema and functions: the daily-incremental job and the
historical-import bulk merge, both in-server ([ADR-0005](0005-java-ingester-and-read-api.md)).
There is no separate DBA process; schema changes ship with ordinary deploys onto a single VPS
([ADR-0011](0011-cheap-eu-vps-hosting.md)) with no blue/green stage to absorb a bad change.
Hand-running SQL by hand gives no record of what has been applied and is easy to get wrong.

## Decision

Adopt a **versioned schema-migration tool, Flyway**, with **plain SQL migrations**
(`V<x.y.z>__description.sql`, semver-versioned) checked into the repo. Flyway has first-class
PostgreSQL and PL/pgSQL support, so the resolution functions, indexes and constraints live in the
same migration files as the tables — one ordered, hashed history in Flyway's
`flyway_schema_history` table.

The schema ships as a **single baseline migration, `V1.0.0__baseline.sql`**, holding the complete
current schema (extensions, reference data, tables, indexes, constraints and the temporal-interval
functions). The prototype's per-concern incremental migrations were squashed into this baseline
once the design settled; this was safe precisely because the prototype carried **no production
data** to preserve. From the baseline forward, every change is a **new, additive,
semver-versioned** migration.

Micronaut's Flyway integration runs migrations **automatically on server startup/deploy**, so
schema and calling code advance together as one Docker artifact. The server deploy is the single
migration authority; the historical-import path runs in the same already-migrated process.
Migrations are **forward-only and backward-compatible**: a new
migration must not break the currently running code, and rollback is by rolling forward, never
by editing an applied migration.

## Consequences

- Schema, PL/pgSQL resolution functions, indexes and constraints are versioned together and
  deploy atomically with their Java callers; no drift between code and DB.
- A clean, reproducible database can be rebuilt from migrations alone — useful for tests and
  for restoring from a `pg_dump` onto a fresh host.
- **Operational risk to call out:** with no blue/green on the single VPS, a bad or slow
  migration is downtime or, worse, data damage. Mitigate with backward-compatible/forward-only
  migrations, the nightly `pg_dump` taken before deploy, and the fact that the BOE corpus can
  be re-ingested.
- Applied migrations are immutable; fixing a mistake means a new migration, which keeps the
  history honest but means every change is permanent.
- A historical import must run against an already-migrated schema; since it runs in the same
  server process that applies migrations on startup, the schema is always present.

## Alternatives considered

- **Liquibase** — capable and PostgreSQL-aware, but its XML/YAML changelog abstraction adds a
  layer over SQL we do not want when the migrations are mostly hand-written PL/pgSQL; heavier
  for no benefit here. Rejected.
- **Hand-run SQL scripts** — no applied-version table, no checksums, no guarantee a migration
  ran exactly once; error-prone and invisible. Rejected.
- **ORM auto-DDL (e.g. schema generated from entities)** — cannot express the PL/pgSQL
  resolution functions, trigram indexes or partial constraints, and hides what actually runs
  against production. Rejected.
