# ADR-0015 — Auto-apply *Fe de erratas* corrections, with an audit trail

## Status

Accepted.

## Context

Sección A documents periodically carry **errata notices** that correct a previously published
act. They reuse the normal company-block markup — a `<p class="articulo">` header (company id and
name) and a `<p class="parrafo">` whose text opens with **"Fe de erratas: Se publicó por
error…"** — and they reference the prior publication by its registry coordinates (Tomo, Folio,
Sección, Hoja, **Inscripción/Asiento**) and describe the fix in free prose, e.g. *"…se publicó
por error … LOPEZ GUERRA JAVIER GERAR4DO, siendo lo correcto … GERARDO"* (see
[BORME-A-2020-12-28](https://www.boe.es/diario_borme/xml.php?id=BORME-A-2020-12-28), which
carries four such notices). Corrections seen in practice fix person-name typos, wrong company
names, and wrong appointee/shareholder identities.

If the correction is ignored, Mercator keeps **known-wrong data**: a misspelt name resolves to
a spurious person, a wrong denominación pollutes search, and the error propagates into the
links built on top. Mercator is also a **data controller** with a GDPR/​LOPDGDD **accuracy**
obligation ([Spec 7](../specs/07-data-protection.md)) — applying an official correction is
squarely in line with it. `FE_ERRATAS` already exists in the act-type catalogue
([Spec 3](../specs/03-extraction.md)); what was undecided is what Mercator *does* with it.

## Decision

**Auto-apply errata corrections to the data they fix, while preserving an audit trail.**

- **Parse, don't apply, in the parser.** The parser recognises the `Fe de erratas:` keyword
  and emits a structured-but-**unresolved** `FE_ERRATAS` record: the **target reference** (the
  company, via its `articulo` header → Hoja+province, plus the referenced
  Inscripción/Asiento and Datos registrales) and the **correction** (erroneous value →
  correct value), with the raw block retained. The parser still never resolves identity
  ([ADR-0007](0007-single-source-of-truth-entity-resolution.md)).
- **Apply in the ingestion path, both write paths.** The in-server ingestion service locates the
  prior act / derived row for the **same company and Inscripción/Datos registrales** and
  rewrites the erroneous value to the correct one — invoked identically by the historical-import
  bulk merge and the daily incremental ([ADR-0006](0006-hybrid-write-path.md)), so corrections
  behave the same on both. When the corrected value is an **entity name**, resolution is re-run for that
  entity, which may re-point or re-score a probabilistic person
  ([ADR-0009](0009-probabilistic-person-resolution.md)).
- **Keep an audit trail; never silently mutate.** The `FE_ERRATAS` act row is always stored,
  the **pre-correction value is preserved**, and the amended row records which errata changed
  it — so every correction is traceable and reversible, and Mercator still never claims its
  data is authentic (only the signed BORME PDF is, [Spec 7](../specs/07-data-protection.md)).
- **Fail safe (conservative bias).** If the target cannot be confidently located, or the
  free-prose correction cannot be confidently parsed into a before→after pair, the errata is
  stored **unapplied and flagged** (and logged for review/retry) rather than guessed — a
  wrong "correction" is worse than an un-applied one, mirroring the resolution policy.
- **Idempotent.** Re-processing the errata document is a no-op: the `FE_ERRATAS` act has the
  usual idempotency key and the application step is guarded by an applied-marker, so it is
  never applied twice ([ADR-0006](0006-hybrid-write-path.md)).

## Consequences

- Downstream entities, search and links see **corrected data**, directly serving the accuracy
  obligation ([Spec 7](../specs/07-data-protection.md)).
- New work: free-prose correction parsing, target matching by Inscripción/Datos registrales,
  re-resolution on name fixes, and audit storage of the pre-correction value.
- The conservative fallback means some erratas remain **unapplied-but-recorded**; these are
  visible and retryable, never silently dropped or mis-applied.
- **Ordering holds in normal cases:** an errata is published *after* the act it corrects, so
  forward date iteration means the target is usually already ingested; if it is not (target
  pre-2009, un-parsed, or not yet merged), the errata is flagged unapplied and can be applied
  on a later pass.
- Suppression flags ([Spec 7](../specs/07-data-protection.md)) are still honoured on a
  corrected row; a correction never resurfaces suppressed identifiers.

## Alternatives considered

- **Record & surface only (store the notice, don't rewrite)** — honest and simple, but leaves
  the known-wrong value in place (spurious persons, wrong names) degrading resolution and
  links; rejected in favour of fixing the data the source itself corrects.
- **Recognise & store raw only (inert)** — captures the block but does nothing with it; weakest
  on accuracy; rejected.
- **Apply but discard the original value** — loses auditability and reversibility and sits
  badly with the "never claim authenticity" stance and the controller audit obligation;
  rejected — the pre-correction value is always kept.
