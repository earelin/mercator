# ADR-0010 — PostgreSQL recursive CTEs over a graph database

## Status

Proposed (pending approval).

## Context

Link detection ([Spec 5](../specs/05-link-detection.md)) needs relationship queries: shared
administrator, shared address, and bounded multi-hop "companies connected through people".
These look graph-shaped, suggesting Neo4j or the in-PostgreSQL Apache AGE extension. But the
core questions are bounded (2–3 hops), the dataset is small (tens of GB), and a second
datastore conflicts with the cheap-and-simple mandate.

## Decision

Implement link detection in **pure PostgreSQL**: indexed self-joins for direct links
(shared admin/address) and `WITH RECURSIVE` for bounded multi-hop traversal, with **cycle
detection** via a carried path array. Do **not** add a graph database or graph extension
initially.

Escalation rule: add the **Apache AGE** extension (which runs *inside* PostgreSQL — same
backups, same connection, openCypher) **only if** a link query needs >3 unbounded hops or
routinely exceeds ~1 s / blows `work_mem`.

## Consequences

- No new infrastructure; relationship queries share the one database and its backups.
- Recursive CTEs require explicit cycle detection and can stress `work_mem` on high-fan-out
  unbounded traversal — hence the bounded-by-default design and the escalation rule.
- For the expected workload, indexed joins/CTEs are fast (practitioner reports put recursive
  CTEs far ahead of graph extensions for bounded traversal at this scale).

## Alternatives considered

- **Neo4j** — a second datastore to run, secure and back up; rejected for initial scale.
- **Apache AGE from day one** — premature; stores graph data in heap tables with per-hop
  B-tree lookups, not faster than CTEs for bounded traversal; deferred to the escalation
  rule.
