# ADR-0021 — BORME data reuse and attribution

## Status

Accepted.

## Context

Mercator republishes and aggregates **data derived from the BORME** through a public read API.
We need a clear legal basis for reusing the BORME *content* — distinct from two adjacent
concerns already decided elsewhere:

- **Code/dictionary licensing** is [ADR-0012](0012-reuse-bormeparser-dictionaries-gpl.md):
  that is about reusing bormeparser's GPLv3 *software*, not about the BORME *data*.
- **Personal-data protection** is [Spec 7](../specs/07-data-protection.md) (GDPR / LOPDGDD):
  that governs the people named in the data, not the right to reuse the dataset itself.

This ADR records the **data-reuse** basis and the resulting attribution policy. The BORME is
public-sector information published by the BOE (Agencia Estatal Boletín Oficial del Estado).
In Spain the reuse of public-sector information is governed by **Law 37/2007** (reuse of
public-sector information), which transposes the EU Open Data / PSI Directive
(**Directive (EU) 2019/1024**), together with the **BOE's own published reuse conditions**.
Those frameworks generally permit broad reuse but attach standard conditions (source
attribution, no distortion, no implied endorsement). The exact current BOE notice and
`robots.txt` are operational details that can change and must be checked before launch.

## Decision

1. **Treat the BORME as reusable public-sector information** under Law 37/2007 and the BOE's
   published reuse conditions, on a **non-exclusive** basis, **for both commercial and
   non-commercial use** — unless the BOE conditions in force at launch state otherwise.
2. **Attribution is mandatory.** Mercator's API responses and documentation must cite the
   **source (BORME / BOE)** and the **retrieval date**, must **not alter the data in a way
   that misleads**, and must **not imply that the BOE endorses Mercator** (the standard PSI
   reuse conditions).
3. **The data is not authentic.** Only the BOE's **signed PDF** is the official record; every
   API response and the docs must surface a disclaimer to that effect. This ties directly to
   [Spec 7](../specs/07-data-protection.md), which already states Mercator never claims
   official/authentic status.
4. **Open verification action (pre-launch).** Before going live, **verify the exact current
   BOE reuse notice and `robots.txt`** and reconcile this ADR with whatever terms are then in
   force. The framing above records *intent*; it is not a settled legal opinion, and the
   precise BOE terms govern.

## Consequences

- Mercator has a defensible, documented basis to republish BORME-derived data via a public
  API, including for downstream commercial consumers (e.g. the public-contracts project).
- Attribution and the "not authentic — only the signed BOE PDF is official" disclaimer become
  **product requirements**, surfaced in API responses and docs, not just internal notes.
- The pre-launch verification of the BOE notice + `robots.txt` is a **hard gate**; it overlaps
  with the `robots.txt` confirmation already tracked in
  [ADR-0018](0018-boe-source-politeness-and-retry.md) and [Spec 7](../specs/07-data-protection.md).
- This ADR is intentionally **about data reuse only** — it does not weaken the GPLv3 obligation
  on ported code ([ADR-0012](0012-reuse-bormeparser-dictionaries-gpl.md)) nor the GDPR
  obligations on personal data ([Spec 7](../specs/07-data-protection.md)); all three apply
  simultaneously.

## Alternatives considered

- **Claim no reuse basis / publish silently** — leaves the project legally exposed and breaches
  the attribution conditions that come with PSI reuse; rejected.
- **Restrict to non-commercial reuse** — unnecessary self-limitation: PSI reuse under Law
  37/2007 generally permits commercial use, and the primary consumer is a sibling project that
  may operate commercially; rejected absent a specific BOE restriction.
- **Treat this as covered by ADR-0012** — conflates *code* licensing (GPLv3) with *data* reuse
  (PSI law); they are different legal regimes and deserve separate records; rejected.
- **Assert the data is authoritative/official to add credibility** — false and contrary to
  [Spec 7](../specs/07-data-protection.md); only the signed BOE PDF is official; rejected.
