# ADR-0002 — Prefer structured text/XML over PDF parsing for Sección A

## Status

Accepted. *(Updated 2026 with the per-document XML finding below — see Context.)*

## Context

The acts Mercator needs live in Sección Primera (BORME-A). Prior art
(bormeparser/libreborme) parses the **PDF**: downloading it, cropping page headers/footers
with PyPDF2, and extracting text — heavier and more fragile, with OCR/layout risk. Avoiding
PDF parsing was the original motivation for this ADR.

Two non-PDF representations of each Sección A document exist, **both confirmed working back
to 2009-01-02** and **both linked directly from each summary item** (`url_xml`, `url_html`)
alongside `url_pdf`:

- **Per-document XML** (`url_xml` → `https://www.boe.es/diario_borme/xml.php?id=BORME-A-…`):
  a clean `<documento>` with `<metadatos>` and a `<texto>` body **pre-segmented** into
  `<p class="articulo">` (company header line) and `<p class="parrafo">` (act text).
- **HTML/plain text** (`url_html` → `txt.php?id=…`): a full HTML page (site chrome +
  metadata header + the same text body) that requires stripping the XML avoids.

In all cases the act *fields* (Constitución, Objeto social, Domicilio, Capital,
Nombramientos, Datos registrales…) remain free Spanish prose inside `<p class="parrafo">`,
so regex/NLP parsing is still required. The choice here is only about the *retrieval format*.

## Decision

Ingest Sección A from the **per-document XML** (`url_xml`) as the primary source; its
`articulo`/`parrafo` segmentation drives company-block construction. Fallback order, per
document and fail-soft: **XML → `txt.php` (strip HTML) → PDF (extract text; authentic last
resort)**. Cache raw responses so re-parsing never re-downloads. Consume `url_xml`/
`url_html`/`url_pdf` straight from the summary rather than constructing URLs.

## Consequences

- No PDF cropping/OCR on the happy path; cleaner input than scraping `txt.php` (structured
  metadata + paragraph segmentation, no chrome to strip).
- The act-block splitter keys off the XML paragraph classes; the text/PDF fallbacks keep the
  header-line → `Datos registrales.` footer heuristic.
- A dependency on **informative-only** endpoints (only the signed PDF is authentic) that are
  not a formally guaranteed API. Mitigated by caching and the XML→txt→PDF fallback chain.

## Alternatives considered

- **`txt.php` as primary** — works, but loses the XML's clean segmentation and needs HTML
  stripping; demoted to first fallback.
- **Parse PDFs (as bormeparser does)** — proven but fragile and heavier; kept as the
  authentic last resort.
- **Treat the per-document XML as fully structured acts** — it is not; act fields are prose
  and still need parsing.
