# Mercator — Specifications

This folder describes **what** Mercator does: its behaviour, inputs, outputs and
guarantees, independent of how it is built. For **how** each capability is implemented,
see [`../features/`](../features/README.md). For **why** the key technical choices were
made, see the [Architecture Decision Records](../architecture/README.md).

## Product summary

Mercator turns Spain's official mercantile gazette — the **BORME** (*Boletín Oficial del
Registro Mercantil*) — into a queryable database and graph of companies, the people
associated with them (administrators, attorneys, liquidators…), and the links between
them (shared administrators, shared registered address, multi-hop relationships).

It ingests BORME data from the BOE open-data service, parses the registered acts,
resolves companies and people into stable entities, and exposes the result through a
read-only HTTP API. Its primary consumer is a sibling **public-contracts** project that
needs to detect related bidders/awardees; the API is otherwise general purpose.

Mercator is designed to be **cheap and simple**: a single EU virtual private server, a Java
ingestion/parsing worker, a PostgreSQL 18 store, and a Java 25 (Micronaut) read API.

## Scope

**V1 is full-featured — there is no reduced MVP.** It covers **every** relevant act type
published in Sección Primera of the BORME (see the
[act-type catalogue](03-extraction.md#act-type-catalogue)), full link detection, and
integration with the contracts project. Constituciones, cambios de domicilio social and
nombramientos/ceses are central, but ampliaciones/reducciones de capital, fusiones,
disoluciones, cambios de objeto/denominación, etc. are all in scope.

Coverage starts on **1 January 2009**, the date the electronic BORME became the official,
authentic edition.

## Glossary

| Term | Meaning |
|------|---------|
| **BORME** | *Boletín Oficial del Registro Mercantil* — daily official gazette of the Mercantile Registry, published by the Agencia Estatal BOE every day except Saturdays, Sundays and Madrid holidays. |
| **BOE** | *Boletín Oficial del Estado* — the state agency that publishes and serves the BORME, including its open-data API. |
| **Sección Primera — Actos inscritos (BORME-A)** | "Empresarios. Actos inscritos" — registered acts per company. **Mercator's target section.** Available as PDF, XML and HTML/text. |
| **Sección Primera — Otros actos (BORME-B)** | "Empresarios. Otros actos publicados en el Registro Mercantil" — other registry filings (depósitos de cuentas, proyectos de fusión/escisión…). A **separate section**, not a sub-apartado of A. Out of scope for V1. |
| **Sección Segunda (BORME-C)** | "Anuncios y avisos legales" — legal notices, grouped into *apartados* by act type. Out of scope for V1. |
| **Sumario (BORME-S)** | The daily summary listing every document published that day. |
| **CVE** | *Código de Verificación Electrónica* — uniquely identifies each act/page for authenticity verification. |
| **Datos registrales** | The registry-coordinate footer of an act, e.g. `T 4337, L 4337, F 155, S 8, H PO 67350, I/A 1 (21.10.20).` |
| **Tomo / Libro / Folio (T/L/F)** | Volume / book / page coordinates within a provincial registry. |
| **Hoja registral (H)** | The company's registry "sheet", e.g. `PO 67350`. Unique within a provincial registry — Mercator's company natural key. |
| **Sección 8 (S 8)** | The registry section for *sociedades* (companies). |
| **Inscripción / Asiento (I/A)** | Entry/booking number within the Hoja. |
| **NIF / CIF** | Spanish tax identifier. **Not published in the BORME** — hence entity resolution cannot rely on it. |
| **CNAE** | Spanish economic-activity classification code, sometimes embedded in act text. |
| **Acto** | A registered act (Constitución, Nombramientos, etc.) within a company block. |
| **Cargo** | A role (Adm. Único, Apoderado, Consejero…) held by a person in a company. |

## Specification index

| # | Spec | Describes |
|---|------|-----------|
| 1 | [Data sources & formats](01-data-sources.md) | The BORME, its sections, formats, coverage and identifier scheme. |
| 2 | [Ingestion](02-ingestion.md) | Historical backfill and daily incremental; resumability and idempotency guarantees. |
| 3 | [Extraction](03-extraction.md) | The fields and act types extracted from each document. |
| 4 | [Data model](04-data-model.md) | The entities stored and their relationships. |
| 5 | [Link detection](05-link-detection.md) | The relationships Mercator computes and answers. |
| 6 | [Public API](06-public-api.md) | The read-only endpoints exposed to consumers. |
| 7 | [Data protection](07-data-protection.md) | Legal basis, GDPR obligations, erasure and etiquette. |
| 8 | [Non-functional requirements](08-non-functional.md) | Cost, residency, performance and technology constraints. |
