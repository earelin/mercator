# ADR-0008 — Registry coordinates (Hoja+province) as the company natural key

## Status

Proposed (pending approval).

## Context

Reliable entity resolution needs a stable key. The obvious candidate — the NIF/CIF — is
**not published in the BORME**. Company names are not unique and change over time (cambio de
denominación). The only stable, published identifier is the registry coordinate in the
*Datos registrales* footer: the **Hoja registral** (e.g. `PO 67350`), which is unique within
its provincial registry.

## Decision

Use **Hoja + province** as the company's natural key. The schema enforces
`UNIQUE (reg_hoja, province_code)`. Name-based fuzzy matching is used only for *search* and
for *cross-source* matching (e.g. to contracts data), not as the primary identity of a
company within Mercator.

## Consequences

- Company identity is deterministic and stable across renames and address changes.
- Acts without a parseable Hoja need a fallback handling policy (flag for review; do not
  silently merge by name).
- Cross-source joins to systems that *do* have NIFs (the contracts project) remain
  name-based and ranked, because the BORME side has no NIF
  ([contracts-integration](../features/contracts-integration.md)).

## Alternatives considered

- **Normalised name as the key** — not unique, changes over time; rejected.
- **Synthetic surrogate only** — needed anyway as the PK, but provides no natural identity
  for idempotent upserts; the natural key is layered on top.
- **NIF** — not available in the source; impossible.
