# ADR-0003 — Use the `datosabiertos` REST summary API over legacy `xml.php`

## Status

Accepted.

## Context

Mercator must enumerate, for each day, which documents the BORME published. Two endpoints
provide the daily summary:

- The **documented REST "datos abiertos" API**:
  `GET https://www.boe.es/datosabiertos/api/borme/sumario/{AAAAMMDD}` (XML or JSON), with a
  published spec (snake_case schema, `APIsumarioBORME.pdf`, June 2024).
- The **legacy** `https://www.boe.es/diario_borme/xml.php?id=BORME-S-YYYYMMDD`, used by
  bormeparser, whose tag casing is older and unconfirmed.

## Decision

Target the **documented `datosabiertos` REST API** as the summary source. Parse either its
XML or JSON form. Do not build against the legacy `xml.php` summary.

## Consequences

- We build against a documented, stable, snake_case schema, reducing parser surprises.
- GET-only over HTTPS; non-publication days return 404 and are skipped.

> **Correction (verified 2026):** an earlier version of this consequence claimed Sección A
> items expose only `url_pdf`. In fact every summary item (A, B and C) exposes `url_pdf`,
> `url_xml` and `url_html`, back to 2009. Per-document XML is the chosen ingestion source —
> see [ADR-0002](0002-structured-xml-over-pdf-parsing.md). This ADR's decision (use the documented
> REST API for the *summary*) is unaffected.

- If we ever need the legacy endpoint, we must first confirm its tag casing against a live
  file.

## Alternatives considered

- **Legacy `xml.php`** — undocumented tag schema, higher risk of silent breakage; rejected
  except as a contingency. **Scope note:** what is rejected here is the *summary* endpoint
  `xml.php?id=BORME-S-YYYYMMDD`. This is **not** the per-document
  `xml.php?id=BORME-A-…` that [ADR-0002](0002-structured-xml-over-pdf-parsing.md) makes the
  primary *document* source — same script name, different role (daily summary vs. one Sección A
  document), opposite verdicts.
- **Scraping the HTML summary pages** — brittle; rejected.
