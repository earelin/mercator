# ADR-0020 — Secrets management on a single VPS

## Status

Accepted.

## Context

Mercator runs as Docker containers on **one cheap EU VPS** ([ADR-0011](0011-cheap-eu-vps-hosting.md)):
a Micronaut read API plus the daily-incremental job; the historical backfill runs from an
offline Java ingester. Several secrets need a home:

- the **PostgreSQL credentials** (app role and ingester role);
- the **backup object-storage credentials** for off-box dumps
  ([ADR-0019](0019-backup-restore-and-retention.md));
- the inbound **API keys** that gate the read API.

[ADR-0013](0013-api-key-auth-and-config.md) already fixed the *style* — 12-factor config,
secrets injected at run time, never committed and never baked into images — but did not say
**where** the secret values actually live on the box, nor how they are scoped and rotated.
The mandate is cheap and simple: one VPS, no extra moving parts. A dedicated secrets
manager would add infrastructure, cost and operational surface out of proportion to a
single-box deployment with a handful of secrets.

## Decision

- **Secrets live only as runtime environment variables**, injected from a single root-owned
  `.env` file (`chmod 600`, **gitignored**) read by Docker Compose / systemd at start.
  Secrets are **never** in the image, **never** in the repo, and **never** in Micronaut
  config files (`application.yml`). The same artifact runs everywhere; only the env differs.
- **Distinct credentials per concern.** DB credentials, object-storage credentials and API
  keys are separate values — no shared password reused across roles.
- **Least-privilege DB roles.** The **app** (read API + daily incremental) uses a
  read-mostly role; the **ingester** uses a separate write/bulk-load role. Neither uses the
  Postgres superuser. Each gets only the grants it needs (see
  [ADR-0007](0007-single-source-of-truth-entity-resolution.md) /
  [ADR-0016](0016-database-schema-migrations.md) for who owns schema and resolution).
- **Documented rotation.** Rotation is: update the value in `.env`, then restart the unit.
  **API keys accept multiple valid values** (a comma-separated list, per
  [ADR-0013](0013-api-key-auth-and-config.md)), so a new key can be added, consumers
  migrated, then the old key removed — **zero-downtime** rotation. DB/storage credential
  rotation tolerates the brief restart window.
- **Secrets are never logged.** Log config and any diagnostics must redact credentials, keys
  and connection strings ([ADR-0017](0017-observability-logging-and-alerting.md)).
- **TLS is mandatory where the API key travels.** API keys must only be sent over HTTPS;
  TLS termination (reverse proxy + Let's Encrypt) is a deployment-operations concern, set up
  with the hosting in [ADR-0011](0011-cheap-eu-vps-hosting.md), not by the application.

## Consequences

- No new infrastructure: secret storage is a file plus filesystem permissions the operator
  already controls. Cheap and easy to reason about.
- A single env file is the source of truth on the box; backups of `/etc` or the deploy dir
  must treat it as sensitive (encrypt or exclude).
- Rotation is a manual, documented runbook step rather than an automated lease — acceptable
  at this scale, and API-key list support keeps the consumer-facing path zero-downtime.
- Least-privilege roles limit blast radius: a leaked app credential cannot bulk-rewrite data.
- The model is local to one host; multi-host or team-scale operation would outgrow it (see
  the upgrade path below).

## Alternatives considered

- **A dedicated secrets manager (HashiCorp Vault / cloud KMS / Doppler / AWS or GCP Secrets
  Manager)** — rejected as over-engineered for one VPS and a few secrets: it adds a service
  to run, secure and pay for, against the cheap-and-simple mandate. **Noted as the upgrade
  path** if Mercator ever scales beyond one box (multiple hosts, a team, automated leasing
  and audit).
- **Secrets in Micronaut `application.yml` / build profiles** — rejected: bakes secrets into
  the artifact and the repo, violating [ADR-0013](0013-api-key-auth-and-config.md).
- **Docker / Compose secrets files mounted as `/run/secrets/*`** — viable and slightly
  stricter than env vars, but heavier to wire for marginal gain on a single box; the
  root-owned `.env` is simpler and already 12-factor. Revisit if moving to Swarm/K8s.
- **One shared credential across app, ingester and backups** — rejected: no per-concern
  scoping, no least privilege, and rotation forces touching everything at once.
