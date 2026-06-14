# docs/CLAUDE.md

Guidance for working in `docs/` — how the documentation is organised and the rules for
authoring it. Project-wide guidance, the tech stack and the architecture invariants live in
the repository-root [`/CLAUDE.md`](../CLAUDE.md).

The docs are the **authoritative design** and must be read before writing code.

## Structure

- `specs/` — **what** the system does (behaviour, no implementation). Start at
  `specs/README.md` (product summary + glossary of BORME terms). Specs are numbered for
  reading order.
- `features/` — **how** each spec is implemented, functionally. Each feature doc ends with a
  sized `## Implementation issues` checklist (the backlog from which GitHub issues will be
  created later). `features/README.md` has the **feature → spec → ADR traceability table**
  and the V1 pipeline.
- `architecture/` — **why**: Architecture Decision Records (MADR-style).
  `architecture/README.md` is the index.

## Scope

**V1 is full-featured — there is no reduced MVP.** The full act-type catalogue, link
detection, the contracts integration and the data-protection tooling are all in V1. Do not
reintroduce `[MVP]`/tier distinctions in specs or features.

## ADR lifecycle & approval

- Author every ADR as **Proposed (pending approval)**. **Never self-accept** — only the
  maintainer (the user) marks an ADR **Accepted**.
- A **Proposed** ADR may be **freely updated in place** — refine the context, change the
  decision, fold in new findings. No superseding ADR is needed while it is Proposed.
- An **Accepted** ADR is **immutable** — change it only by writing a new ADR that supersedes
  it, updating the old one's status to point at the replacement.
- Prefer updating the existing Proposed ADR when refining the *same* decision; create a new
  Proposed ADR for a genuinely *new* decision.
- Keep each ADR's `## Status` line and the status column in `architecture/README.md` in sync.

## Authoring conventions

- **All docs in English**; keep Spanish BORME domain terms verbatim (BORME, *Datos
  registrales*, Hoja…) — they are defined in the glossary in `specs/README.md`.
- Filenames: ADRs `NNNN-kebab-title.md` (zero-padded, sequential); spec files numbered for
  reading order; feature files kebab-case.
- Each feature doc follows the template: **Summary → Related specs/ADRs → Functional
  behaviour → Data flow → Inputs/outputs → Edge cases → Acceptance criteria → Implementation
  issues**.
- **Diagrams use Mermaid** (` ```mermaid ` fenced blocks: `flowchart` for data flows,
  `erDiagram` for the data model, `graph` for trees) — not ASCII art. Reserve plain code
  fences for literal samples (BORME act text, XML, SQL).
- Cross-link liberally (specs ↔ features ↔ ADRs); keep links relative and valid.
- No GitHub issues are created yet — the `## Implementation issues` checklists are the backlog.
