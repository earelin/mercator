# Spec 3 — Extraction

**Realised by:** [act-parsing](../features/act-parsing.md),
[entity-extraction-normalisation](../features/entity-extraction-normalisation.md).
**Constrained by:** [ADR-0002](../architecture/0002-structured-xml-over-pdf-parsing.md),
[ADR-0012](../architecture/0012-reuse-bormeparser-dictionaries-gpl.md).

## What this describes

The information Mercator extracts from each Sección A document, and the catalogue of act
types it recognises.

## Document anatomy

A Sección A document is a sequence of **company blocks**. In the source XML
([Spec 1](01-data-sources.md)) these map to `<p class="articulo">` (company header line,
often `NNNNN - COMPANY NAME SL.`) followed by `<p class="parrafo">` (that company's acts).
Each block starts with a company name line and ends with a `Datos registrales.` footer. A
block contains **one or more acts**, each introduced by a known act keyword. Example:

```text
COMPANY NAME SL.
Constitución. Comienzo de operaciones: 16.09.20. Objeto social: …. Domicilio: C/ GRAN VIA 112 BAJO (VIGO). Capital: 4.000,00 Euros.
Declaración de unipersonalidad. Socio único: ZINELKELMA SALIMA.
Nombramientos. Adm. Unico: ZINELKELMA SALIMA.
Datos registrales. T 4337, L 4337, F 155, S 8, H PO 67350, I/A 1 (21.10.20).
```

## Fields extracted

Per **company block**:

- Company **raw name** (verbatim) and **normalised name**.
- **Legal form** (SL, S.L., SA, SLU, SAU, SCP, AIE…), used to tell companies from people.
- **Registry coordinates** from Datos registrales: Tomo, Libro, Folio, registry section,
  **Hoja** (e.g. `PO 67350`), Inscripción/Asiento, and the act date.
- Province (derived from the document's province block).

Per **act**:

- **Act type** (normalised enum — see catalogue below).
- Act-specific payload: for cargo acts, a map of **role → [person names]**; for
  Constitución, the *objeto social*, *capital*, *domicilio*, *comienzo de operaciones*;
  for Cambio de domicilio, the new address; etc.
- The raw act text block (kept for audit and re-parsing).

Per **person** mentioned in a cargo act:

- **Raw name** (verbatim) and **normalised name**.
- The **role** (cargo) held, mapped to a canonical enum.
- The **event** (nombramiento, cese, reelección, revocación).

Per **address** (constitución / cambio de domicilio):

- **Raw text**, **normalised text**, and where parseable, **municipality** and province.

## Act-type catalogue

V1 recognises **all** of the following act types (there is no reduced subset). Mercator
normalises every spelling/spacing variant to a canonical enum.

| Act type | Enum |
|----------|------|
| Constitución | `CONSTITUCION` |
| Cambio de domicilio social | `CAMBIO_DOMICILIO` |
| Nombramientos | `NOMBRAMIENTO` |
| Ceses / Dimisiones | `CESE` |
| Reelecciones | `REELECCION` |
| Revocaciones | `REVOCACION` |
| Cambio de objeto social | `CAMBIO_OBJETO` |
| Cambio de denominación social | `CAMBIO_DENOMINACION` |
| Declaración de unipersonalidad | `UNIPERSONALIDAD` |
| Disolución | `DISOLUCION` |
| Extinción | `EXTINCION` |
| Reapertura hoja registral | `REAPERTURA` |
| Ampliación de capital | `AMPLIACION_CAPITAL` |
| Reducción de capital | `REDUCCION_CAPITAL` |
| Fusión por absorción | `FUSION` |
| Modificaciones estatutarias | `MODIF_ESTATUTOS` |
| Otros conceptos | `OTROS` |
| Fe de erratas | `FE_ERRATAS` |

> Adding a further act type later requires no schema change — only a new enum value and
> parser branch (see [Spec 4](04-data-model.md)).

### Corrections (Fe de erratas)

`FE_ERRATAS` is a **correction act**, not a normal company act. Its `<p class="parrafo">`
opens with **"Fe de erratas: Se publicó por error…"** and references a prior publication by
its Inscripción/Asiento and Datos registrales. Mercator extracts the **target reference** (the
company + the corrected publication's coordinates) and the **change** (erroneous value →
correct value), then **auto-applies** the fix to the previously ingested act/entity while
keeping the pre-correction value as an audit trail; an un-matchable or ambiguous correction is
recorded **unapplied and flagged**, never guessed. See
[errata-corrections](../features/errata-corrections.md) and
[ADR-0015](../architecture/0015-auto-apply-fe-de-erratas-corrections.md).

## Roles (cargos)

Cargo acts use standardised abbreviations, all mapped to a canonical enum: `Adm. Unico`,
`Adm. Solid.`/`ADM.SOLIDAR.`, `Adm. Mancom.`, `Consejero`, `Presidente`, `Secretario`,
`Cons.Del.Sol`, `Con.Delegado`, `Apoderado`, `Apo.Sol.`, `Apo.Manc.`, `Liquidador`,
`LiqUnico`, `Auditor`, `Aud.C.Con.`, `Socio único`. Multiple names within a role are
separated by `;`.

## Quality target

≥95% of acts in a representative sample (covering Barcelona, Madrid and Pontevedra format
variants) parse without error before the parser is considered ready to advance.

## Known difficulties (must be handled)

- **Person vs company disambiguation** fails when a company acts as administrator/auditor
  (e.g. `DELOITTE SL` as Auditor) — resolved via the legal-suffix heuristic.
- **Name ordering** is inconsistent: usually `APELLIDO1 APELLIDO2 NOMBRE` (surname-first),
  sometimes `NOMBRE APELLIDOS`. Both raw and a normalised key are stored.
- **Role spelling/spacing** varies (`Adm. Unico` vs `ADM.UNICO`); all map to one enum.
- **Format drift over the years**: older documents use numbered entries
  (`78291 - NAME SL.`); Barcelona embeds CNAE codes (`ACTIVIDAD PRINCIPAL: 4712 / …`).
- **Encoding**: text mixes Latin/UTF-8 with Spanish characters (Ñ, accents); normalise
  carefully.
- **Multi-act blocks**: one block often carries several acts sharing one Datos registrales
  footer.
