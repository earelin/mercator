# ADR-0009 — Probabilistic person resolution with confidence scores

## Status

Proposed (pending approval).

## Context

Unlike companies ([ADR-0008](0008-registry-coordinates-as-company-natural-key.md)),
**persons in the BORME have no identifier at all** — no NIF, no registry coordinate, and
names are published inconsistently (surname-first vs name-first, abbreviations, accents).
Two different people can share a name; one person appears under spelling variants. Treating
person identity as exact would both create false merges and produce false "links" between
unrelated companies — a data-quality and a GDPR-accuracy problem.

## Decision

Model person identity as **probabilistic**. Resolution combines a normalised-name key with
corroboration (co-occurrence in the same company/registry) and produces a **confidence
score**. Person-based links between companies are stored and surfaced as **scored
candidates, never as established facts.** The API returns the confidence alongside every
person-derived result.

## Consequences

- Mercator avoids conflating individuals and over-stating links — important legally
  ([Spec 7](../specs/07-data-protection.md)) and analytically.
- Consumers (including the contracts project) must handle candidate links with scores, not
  booleans.
- Tuning the scoring thresholds is an ongoing concern; conservative defaults are preferred
  (a missed merge is a duplicate; a wrong merge is corruption/defamation).

## Alternatives considered

- **Exact name matching as identity** — produces false merges and false links; rejected.
- **No person resolution at all** — loses the core "shared administrator" link, the
  project's main value; rejected.
