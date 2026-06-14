# Feature — Link queries

## Summary

The relationship queries — shared administrator, shared registered address, and bounded
multi-hop connections — implemented as indexed joins and recursive CTEs inside PostgreSQL.

## Related specs / ADRs

- Specs: [5 — Link detection](../specs/05-link-detection.md)
- ADRs: [0010 — Recursive CTEs over graph DB](../architecture/0010-postgresql-ctes-over-graph-db.md)

## Functional behaviour

- **Shared administrator** — self-join `appointment` on `person_id`. Spans the **full
  appointment history** by default (companies that *ever* shared an administrator, current or
  past roles), returning the two companies, the shared (resolved) person and the role(s);
  optionally constrained to overlapping validity for people who held the roles at the same
  time (then also returning the overlapping period). Carries the person's **confidence**.
- **Shared registered address** — self-join `company_address` on `address_id` (stable via
  `resolve_address`'s exact-normalised key). Spans the **full address history** by default
  (companies that were *ever* domiciled at the same address, current or past intervals);
  optionally constrained to overlapping validity for companies co-located at the same time.
- **Multi-hop connection** — `WITH RECURSIVE` traversal of the company⇄person graph, bounded
  by a hop limit, with **cycle detection** via a carried path array; returns the path
  between two companies (or all companies within N hops of one).
- **Temporal classification** — every shared-administrator and shared-address link is
  tagged with whether it is a **current/overlapping** match (the two companies shared the
  administrator or address at the same time) or a **historical** match (the link exists only
  across non-overlapping intervals — they were connected at different times). The result
  reports this `temporal` flag explicitly so consumers never mistake a past coincidence for a
  live connection.
- **Confidence propagation** — any link routed through a person is a **scored candidate**,
  not a fact ([ADR-0009](../architecture/0009-probabilistic-person-resolution.md)); the
  result carries the aggregated confidence. **A historical (non-overlapping) match lowers
  the reported confidence** relative to an otherwise-identical current/overlapping match —
  weaker evidence of an active relationship — and this penalty applies to both
  person-derived links *and* the otherwise-deterministic shared-address link.

## Data flow

```mermaid
flowchart LR
    ID["company / person id(s)"] --> Q["join / WITH RECURSIVE<br/>bounded + path array"] --> L["links (+confidence)"] --> API["API"]
```

## Inputs / outputs

- **Input:** a company or person id, optional hop limit / date filter.
- **Output:** linked companies/people with link type, period, a **temporal flag**
  (current/overlapping vs. historical) and confidence (reduced for historical matches).

## Edge cases

- **Cycles** — prevented by the path array; never infinite-loop.
- **High fan-out** — bounded hop limit guards `work_mem`; document any truncation.
- **No NIF for persons** — person-based links remain candidates.
- **Performance ceiling** — if a query needs >3 unbounded hops or exceeds ~1 s / blows
  `work_mem`, escalate to Apache AGE per [ADR-0010](../architecture/0010-postgresql-ctes-over-graph-db.md).

## Acceptance criteria

- Shared-admin and shared-address queries return correct, deduplicated links with periods.
- Each shared-admin/shared-address link is tagged current/overlapping vs. historical, and a
  historical match reports lower confidence than an equivalent current one.
- Multi-hop traversal terminates with cycle detection and respects the hop limit.
- Person-derived links carry confidence and are never labelled as certain.

## Implementation issues

- [ ] Shared-administrator query (full-history self-join, optional overlap constraint) + confidence.
- [ ] Shared-registered-address query.
- [ ] Bounded multi-hop recursive CTE with path-array cycle detection.
- [ ] Temporal classification (current/overlapping vs. historical) tagged on shared-admin
      and shared-address links.
- [ ] Confidence aggregation across a path, including the historical-match penalty.
- [ ] Query performance benchmarks + the AGE escalation decision gate.
- [ ] API endpoints exposing the above (with [read-api](read-api.md)).
