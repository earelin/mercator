# Feature — Summary enumeration

## Summary

Discover, for any date or date range, which Sección A documents the BORME published, by
querying the BOE `datosabiertos` summary REST API and extracting the list of
`BORME-A-YYYY-NNN-PP` identifiers.

## Related specs / ADRs

- Specs: [1 — Data sources](../specs/01-data-sources.md), [2 — Ingestion](../specs/02-ingestion.md)
- ADRs: [0003 — REST over legacy XML](../architecture/0003-datosabiertos-rest-api-over-legacy-xml.md), [0018 — BOE source politeness & retry](../architecture/0018-boe-source-politeness-and-retry.md)

## Functional behaviour

- Given a date `AAAAMMDD`, request
  `GET https://www.boe.es/datosabiertos/api/borme/sumario/{AAAAMMDD}` with an explicit
  `Accept` (XML or JSON).
- Parse the response into the summary tree (`data → sumario → diario → seccion → item`).
- Select **Sección A** (`seccion[@codigo="A"]`) items and collect each item's
  `identificador` (e.g. `BORME-A-2026-9-01`), `titulo` (province), and the
  `url_xml`/`url_html`/`url_pdf` the summary provides for every item.
- **Identifier handling:** the `identificador` is treated as an **opaque string**, used
  verbatim as `borme_id` and as the cache key. The `BORME-A-YYYY-NNN-PP` notation is
  illustrative only — Mercator never zero-pads, parses, or reconstructs the parts (real ids
  like `BORME-A-2026-9-01` mix unpadded and padded fields), so no padding assumption is baked
  in anywhere.
- Use each item's **`url_xml`** as the document source (no URL construction needed); it can
  also be reconstructed as `xml.php?id={identificador}` if absent.
- Ignore Sección **B** (*otros actos*) and **C** (*anuncios*) for now — they are enumerable
  the same way and reserved for later scope.
- For a range, iterate dates from the **caller-supplied start** to the end date, **skipping
  404s** (non-publication days) as a normal outcome. The lower bound is owned by the caller
  ([historical-backfill](historical-backfill.md), CLI-configurable); `2009-01-02` is the default
  earliest date for which the per-document XML is available. A day that returns 200 but contains
  **no Sección A items** is also a normal "nothing to ingest" outcome (logged `SKIPPED`), not an
  error.

## Data flow

```mermaid
flowchart LR
    D["date(s)"] --> S["GET sumario REST"] --> P["parse"]
    P --> I["Sección A items<br/>id, province, url_xml, url_html, url_pdf"]
    I --> W["work list → document-fetch"]
```

## Inputs / outputs

- **Input:** a single date or an inclusive date range.
- **Output:** a list of document descriptors `{borme_id, province, url_xml, url_html, url_pdf}`
  per publication day — the exact shape [document-fetch](document-fetch.md) consumes as input;
  an explicit "no publication" signal for 404 days. *(`pages` is a per-document field read later
  from the XML `<metadatos>`, not known at summary time, so it is not part of this descriptor.)*

## Edge cases

- **404** → non-publication day; skip, do not error.
- **200 but no Sección A items** → nothing to ingest that day; record `SKIPPED`, not an error.
- **5xx / 429** → retry with backoff via the **single shared rate limiter / retry policy**
  ([ADR-0018](../architecture/0018-boe-source-politeness-and-retry.md)) used by both this and
  [document-fetch](document-fetch.md) — one global ≤1–2 req/s budget, not two.
- **JSON vs XML shape differences** — JSON drops `response`/`item` wrappers and arrays the
  collections; support whichever `Accept` is configured.
- **Sección B and C present** — enumerate but skip them (out of scope for V1).
- **Per-document URLs present** — `url_xml`/`url_html`/`url_pdf` are supplied for every item
  (A/B/C) back to 2009; still fail soft per document if a representation is missing.
- **Year rollover** — `NNN` (diario number) resets yearly; never assume monotonicity across
  years.

## Acceptance criteria

- For a known publication day, returns the exact set of Sección A identifiers present in the
  official summary.
- For a known non-publication day, returns "no publication" without raising.
- Enumerates an arbitrary range 2009→today without gaps or duplicates.

## Implementation issues

- [ ] Summary REST client: fetch `sumario/{AAAAMMDD}`, handle 404/5xx/429 via the **single
      shared rate limiter/retry policy** (shared with [document-fetch](document-fetch.md), not a
      second limiter), configurable `Accept`.
- [ ] Summary parser (XML) → typed document descriptors for Sección A items.
- [ ] Date iterator over an inclusive range with non-publication-day skipping.
- [ ] Capture per-item `url_xml`/`url_html`/`url_pdf`; fallback constructor `xml.php?id={id}` if missing.
- [ ] JSON summary parser variant (parity with XML).
- [ ] Unit tests with recorded summary fixtures (a publication day + a holiday).
