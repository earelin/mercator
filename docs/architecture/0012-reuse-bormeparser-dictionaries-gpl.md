# ADR-0012 — Reuse bormeparser dictionaries (GPL)

## Status

Proposed (pending approval).

## Context

Parsing BORME act text correctly requires a large body of domain knowledge: the act
vocabulary, role/cargo abbreviations and their canonical forms, province codes, and
company-vs-person heuristics. The open-source `bormeparser` project (GPLv3, by Pablo
Castellano) already encodes all of this in `regex.py`, `acto.py`, `cargo.py`, `clean.py`,
`provincia.py`, `sociedad.py`. It is unmaintained and parses PDFs, but its dictionaries are
directly reusable. Mercator is already GPLv3.

Among the open-source BORME tooling surveyed, **`libreborme`** — the Django web application
([github.com/PabloCastellano/libreborme](https://github.com/PabloCastellano/libreborme))
built on top of `bormeparser` — produced the **best extraction results**, which is what
makes its underlying parser the most attractive prior art to port.

## Decision

**Reuse bormeparser's regex and act/role/province dictionaries** rather than rebuilding this
domain knowledge from scratch, driving them from `txt.php` text
([ADR-0002](0002-txt-php-over-pdf-parsing.md)) instead of PDF cropping. Because bormeparser
is GPLv3 and Mercator is GPLv3, the derived parser stays GPL-compatible. If a different
license were ever required, the dictionary tables would be reimplemented cleanly instead.

## Consequences

- Large head start on parser accuracy and the act/role vocabulary.
- Mercator's parser is a GPLv3 derivative — the project remains GPLv3 (consistent with its
  existing LICENSE).
- bormeparser/libreborme are treated as **reference implementations, not runtime
  dependencies** (they are unmaintained; libreborme's public site went offline ~April 2025).

## Alternatives considered

- **Build all dictionaries from scratch** — slower and error-prone; only justified if a
  non-GPL license were mandatory; not the case here.
- **Other open-source BORME parsers** — evaluated, but `libreborme`/`bormeparser` gave the
  best extraction results of the surveyed options, so they are the chosen prior art.
- **Depend on bormeparser as a library at runtime** — unmaintained and PDF-oriented;
  rejected in favour of vendoring/porting the dictionaries.
