# ADR-0004 — PostgreSQL as the single primary datastore

## Status

Proposed (pending approval).

## Context

Mercator stores companies, people, addresses, acts and temporal relationships, and must
support fuzzy name matching (entity resolution) and relationship traversal (link
detection). Prior art ranges from Elasticsearch stacks (osbex) to relational models
(libreborme/Django). The project mandates low cost and operational simplicity, and the
contracts project must read the same data.

## Decision

Use **PostgreSQL 18 as the single primary datastore**, enabling the `pg_trgm`,
`fuzzystrmatch` and `unaccent` extensions. All entity resolution (trigram/fuzzy matching)
and link queries (indexed joins, recursive CTEs) run inside PostgreSQL. No second datastore
is introduced initially.

## Consequences

- One engine to operate, back up (`pg_dump`) and secure — fits the single-VPS budget
  ([ADR-0011](0011-cheap-eu-vps-hosting.md)).
- Fuzzy matching and graph-ish traversals are handled natively (GIN trigram indexes;
  `WITH RECURSIVE`).
- Heavy/deep graph traversal is not PostgreSQL's strength; if needed, the in-database
  Apache AGE extension is the escalation, not a new datastore
  ([ADR-0010](0010-postgresql-ctes-over-graph-db.md)).
- Both write paths and both read consumers share one schema and one connection model.

## Alternatives considered

- **Elasticsearch (osbex-style)** — heavier, extra infra, weaker relational/temporal
  modelling; rejected.
- **A dedicated graph database (Neo4j)** — second datastore, extra cost/ops; rejected for
  the initial scale ([ADR-0010](0010-postgresql-ctes-over-graph-db.md)).
