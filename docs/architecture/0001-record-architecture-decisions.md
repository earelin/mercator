# ADR-0001 — Record architecture decisions

## Status

Proposed (pending approval).

## Context

Mercator is a greenfield project with several non-obvious architectural choices already
implied by its research phase (text vs PDF parsing, where entity resolution lives, single
database vs graph store, etc.). Future contributors — and the sibling contracts project —
need to understand *why* those choices were made, not just *what* the code does.

## Decision

We keep a log of Architecture Decision Records in `docs/architecture/`, one Markdown file
per decision, named `NNNN-kebab-title.md` with a zero-padded sequence number. Each ADR uses
the format: **Status, Context, Decision, Consequences, Alternatives considered**.

**Lifecycle & approval.** Each ADR is authored as **Proposed (pending approval)**. While an
ADR is **Proposed it may be freely updated in place** — refine the context, change the
decision, fold in new findings. Only the **maintainer** moves an ADR to **Accepted**; ADRs
are never self-accepted. An **Accepted** ADR is **immutable** — change it only by writing a
new ADR that supersedes it, updating the old one's status to point at the replacement.

Prefer updating the existing Proposed ADR when refining the *same* decision; create a new
(Proposed) ADR for a genuinely *new* decision. `README.md` is the index.

## Consequences

- Decisions are discoverable and reviewable in version control alongside the code.
- A small, ongoing documentation cost per significant decision.
- Specs and feature docs cross-link to ADRs to explain their constraints.

## Alternatives considered

- **No formal record** — relies on tribal knowledge; rejected for a multi-project,
  open-source effort.
- **A single decisions page** — harder to review per-decision and to mark supersession;
  rejected in favour of one file per decision.
