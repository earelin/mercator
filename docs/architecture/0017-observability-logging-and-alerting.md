# ADR-0017 — Observability: logging, metrics, heartbeat and alerting

## Status

Accepted.

## Context

Mercator runs **unattended on one cheap EU VPS** ([ADR-0011](0011-cheap-eu-vps-hosting.md))
in Docker. The load-bearing operational risk is a **silently-failing daily-incremental job**:
the Micronaut `@Scheduled` task in the `server` ([ADR-0005](0005-java-ingester-and-read-api.md),
[ADR-0006](0006-hybrid-write-path.md)) is the only thing keeping the graph current. If it
errors, throws on startup, or simply stops firing, **no one is watching** — the API keeps
answering happily while the data quietly goes stale. We need to know *that* the job ran and
*that* it succeeded, ideally before a human notices missing companies.

The mandate is cheap and simple, so observability must be **proportionate to a hobby-scale
single VPS**: enough to detect failure and diagnose it, with near-zero extra cost and no new
moving parts to babysit. A full metrics-and-logs platform is out of scope.

## Decision

- **Structured JSON logging via SLF4J + Logback to stdout.** Logs go to stdout/stderr and are
  captured by Docker/journald (`docker logs` / `journalctl`); no log files to rotate on the
  VPS. Each daily-job run logs start, document counts, and a clear success/failure line with a
  correlation id. JSON keeps logs greppable and machine-parseable if we ever ship them off-box.
- **Micronaut Micrometer metrics + a Management `/health` endpoint.** Enable
  `micronaut-management` and Micrometer to expose `/health` (DB connectivity, readiness) and
  basic JVM/job counters. `/health` is already noted as a probe exception in
  [ADR-0013](0013-api-key-auth-and-config.md) and stays **unauthenticated**.
- **A persisted last-success heartbeat for the daily job.** Each successful run records a
  timestamp + outcome in the DB — a small `job_run` record (or a marker in `borme_log`) — so
  **staleness is queryable in SQL** (`now() - last_success`). This is the source of truth for
  "is the job alive?", independent of whether the process is up.
- **Cheap alerting via a dead-man's-switch.** On a successful daily run the job pings an
  external **healthchecks.io-style** cron monitor; if the expected ping doesn't arrive within
  the grace window, the *external* service alerts (email/webhook). Optionally the job also
  sends a direct email/webhook on caught failure. The dead-man's-switch is essential because it
  fires even when the whole VPS or container is down — the in-process path cannot.

The endpoint/table names above are proposed defaults, to be finalised in implementation.

## Consequences

- A stalled or erroring nightly job is **detected within ~a day** without anyone watching the
  box, satisfying the core risk.
- Near-zero added cost and ops surface: logging and `/health` are built into Micronaut; the
  cron monitor's free tier covers a single daily ping.
- The heartbeat makes staleness a first-class, queryable fact — usable by `/health` and by ops.
- Logs live only in journald/Docker on one host; deep historical log search is not available
  (acceptable at this scale — re-ingestion is always possible).
- One external dependency (the cron-monitor service); if it lapses we lose alerting but not
  function. A self-hosted ping target can replace it later without changing the job.

## Alternatives considered

- **Prometheus + Grafana + Alertmanager, or an ELK/Loki stack** — the "real" observability
  answer, but far too heavy and costly (RAM, disk, extra containers) for one ~€7/month VPS;
  rejected as disproportionate to the mandate. Micrometer already exposes a Prometheus scrape
  format, so this remains an option if the project ever outgrows a single host.
- **Hosted APM/SaaS observability (Datadog, New Relic…)** — strong, but a recurring cost that
  conflicts with the budget; rejected.
- **No heartbeat, rely on logs/`/health` only** — `/health` reports liveness, not *freshness*,
  and nobody reads logs unattended; a silent job stall would go unnoticed. Rejected.
- **Email-only alerting from inside the job** — cannot fire when the VPS/container is down,
  exactly the failure mode that matters most; kept only as a complement to the dead-man's-switch.
