# Spec 7 — Data protection

**Realised by:** [data-protection](../features/data-protection.md),
[document-fetch](../features/document-fetch.md) (etiquette).
**Constrained by:** [ADR-0011](../architecture/0011-cheap-eu-vps-hosting.md) (EU residency),
[ADR-0021](../architecture/0021-borme-data-reuse-and-attribution.md) (data-reuse legal basis &
attribution), [ADR-0019](../architecture/0019-backup-restore-and-retention.md) (retention &
encrypted backups), [ADR-0018](../architecture/0018-boe-source-politeness-and-retry.md)
(BOE etiquette & robots.txt).

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
  and a **legitimate-interest** basis (GDPR Art. 6(1)(f)) for reuse in a transparency/anti-fraud
  tool.
- **The legitimate-interest basis is a conclusion that must be documented, not assumed.**
  Mercator maintains a written **Legitimate Interests Assessment (LIA)** — the three-part
  purpose / necessity / balancing test — because aggregating individuals across companies into a
  graph goes beyond the source's per-document purpose and is exactly the reuse that needs a
  recorded balancing test. The LIA is revisited when processing materially changes. Reuse of the
  *content* as public-sector information also rests on Spain's Law 37/2007 and the BOE reuse
  conditions ([ADR-0021](../architecture/0021-borme-data-reuse-and-attribution.md)).
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

## Accuracy

- The BORME publishes **Fe de erratas** notices that correct earlier entries. Mercator
  **auto-applies** these corrections to the data they fix (keeping an audit trail), so its
  output reflects the corrected record rather than a known error — supporting the GDPR
  accuracy obligation ([ADR-0015](../architecture/0015-auto-apply-fe-de-erratas-corrections.md)).
  A correction never resurfaces a suppressed identifier and never makes the data
  authentic — only the signed PDF is.

## Residual identifiers

- Some older entries contain DNI/NIE. Mercator **suppresses these residual personal
  identifiers**. Because storing them re-creates the very PII risk, DNI/NIE are detected
  (pattern + control-letter check) and **dropped at extraction** — they are *never persisted* to
  the live model — and the serve layer keeps a backstop filter against any that slip through.
  Suppression of a *named individual* (below) is different: it is a serve-time filter on a
  persisted flag, not destruction of the act record.

## Retention (storage limitation)

- GDPR Art. 5(1)(e) requires data not be kept longer than needed. Mercator keeps the derived
  company/person/act model **as long as the corresponding fact remains public at the BOE** (the
  transparency purpose persists), but bounds the **ancillary** stores: the raw fetch cache, the
  `erasure_log`, and database backups each have an explicit retention window
  ([ADR-0019](../architecture/0019-backup-restore-and-retention.md)). Backups contain personal
  data and are therefore **encrypted at rest** and EU-resident.

## Etiquette toward the BOE

- Use the **documented REST API**, identify the client with a **User-Agent**, **rate-limit**
  politely (with backoff on 429/5xx), and **cache** responses — both to respect the service and
  to avoid IP blocks ([ADR-0018](../architecture/0018-boe-source-politeness-and-retry.md)).
- **robots.txt is a pre-launch checklist item, not a vague caveat:** the BOE `robots.txt`
  restricts some automated paths, so the chosen `datosabiertos` / `xml.php` / `txt.php` paths
  **must be confirmed permitted** (and the reuse notice re-read) before the public launch.
