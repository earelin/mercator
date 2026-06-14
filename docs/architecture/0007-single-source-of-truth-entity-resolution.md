# ADR-0007 — Single source of truth for entity resolution

## Status

Accepted.

## Context

The hybrid write path ([ADR-0006](0006-hybrid-write-path.md)) has two writers: the backfill
merge (SQL) and the daily ingest service (Java). Entity resolution — deciding whether a
parsed company/person is new or matches an existing one — is the riskiest logic in the
system (a wrong merge destroys data). If each path implemented its own resolution, they
would inevitably diverge.

## Decision

**Resolution lives in exactly one place**, invoked identically by both write paths. The
chosen mechanism is a set of **shared PL/pgSQL functions** — `resolve_company(...)` and
`resolve_person(...)` — since resolution is mostly `pg_trgm`/`fuzzystrmatch` queries anyway,
wrapped by the `shared` library's `IngestionService`. The `ingester`'s backfill merge and the
`server`'s scheduled daily job both call that one service. One definition, both callers.

Corollary: **the parser never resolves identity.** It emits normalised-but-unresolved
records (it computes `norm_name`, extracts `reg_hoja`, etc., but does not decide "is this an
existing company"). Resolution happens DB-side (or, if ever moved, in one Java service)
only.

## Consequences

- Both write paths produce identical resolution decisions by construction.
- Resolution logic is testable in isolation as database functions.
- The ingester is simpler and stateless with respect to identity.
- An acceptable alternative implementation is a Java batch `--backfill` mode (in the ingester
  or the API) reusing the same resolution code; either way the rule "one definition, both
  callers" holds.

## Alternatives considered

- **Resolution in each path** — guaranteed drift between backfill and daily; rejected.
- **Resolution in the ingester/parser** — would put identity logic in the one component that
  must stay stateless and re-runnable; rejected.
