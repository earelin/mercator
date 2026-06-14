# Spec 7 — Data protection

**Realised by:** [data-protection](../features/data-protection.md),
[document-fetch](../features/document-fetch.md) (etiquette).
**Constrained by:** [ADR-0011](../architecture/0011-cheap-eu-vps-hosting.md) (EU residency).

## What this describes

The legal basis under which Mercator reuses BORME data, and the obligations and behaviours
that follow from the personal data it contains.

## Legal basis for reuse

- The BORME is **official public-sector information** explicitly published for legal
  transparency. The BOE offers a reuse API and reuse conditions; any download implies
  acceptance of them.
- Administrators', attorneys' and liquidators' names are published because the Código de
  Comercio and Reglamento del Registro Mercantil **mandate publicity** of these acts for
  legal certainty. This gives a strong public-interest basis for the original publication
  and a **legitimate-interest** basis for reuse in a transparency/anti-fraud tool.
- Mercator **cites the source** and never claims official/authentic status — only the
  signed PDF is authentic.

## Controller obligations

Because the data contains personal data, Mercator (its operator) is a **data controller**
under GDPR and Spain's LOPDGDD (LO 3/2018). It therefore must:

- Publish a **privacy notice** describing what is processed, why, and the lawful basis.
- Expose personal data only at the **granularity needed** for the transparency purpose.
- Keep an **audit/erasure-request log**.
- Host within the **EU** to simplify residency (see [Spec 8](08-non-functional.md)).

## Right to erasure / suppression

- Expect requests under GDPR Art. 17 ("derecho al olvido"). Because the source remains
  lawfully public at the BOE, Mercator has grounds to refuse outright deletion of the
  underlying public fact.
- Mercator must nonetheless provide a **documented process** and the ability to
  **suppress/limit** an individual's visibility/indexing on justified request.

## Residual identifiers

- Some older entries contain DNI/NIE. Mercator **suppresses these residual personal
  identifiers** from its output even when present in the source.

## Etiquette toward the BOE

- Use the **documented REST API**, identify the client with a **User-Agent**, **rate-limit**
  politely (with backoff on 429/5xx), and **cache** responses — both to respect the service
  and to avoid IP blocks. The BOE `robots.txt` restricts some automated paths.
