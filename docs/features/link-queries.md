# Feature — Link queries

## Summary

The relationship queries — shared administrator, shared registered address, and bounded
multi-hop connections — implemented as indexed joins and recursive CTEs inside PostgreSQL.

## Related specs / ADRs

- Specs: [5 — Link detection](../specs/05-link-detection.md)
- ADRs: [0010 — Recursive CTEs over graph DB](../architecture/0010-postgresql-ctes-over-graph-db.md)

## Functional behaviour

- **Shared administrator** — self-join `appointment` on `person_id` where validity overlaps;
  return the two companies, the shared (resolved) person, the role(s) and the overlapping
  period. Carries the person's **confidence**.
- **Shared registered address** — self-join `company_address` on `address_id` (stable via
  `resolve_address`'s exact-normalised key). Spans the **full address history** by default
  (companies that were *ever* domiciled at the same address, current or past intervals);
  optionally constrained to overlapping validity for companies co-located at the same time.
- **Multi-hop connection** — `WITH RECURSIVE` traversal of the company⇄person graph, bounded
  by a hop limit, with **cycle detection** via a carried path array; returns the path
  between two companies (or all companies within N hops of one).
- **Confidence propagation** — any link routed through a person is a **scored candidate**,
  not a fact ([ADR-0009](../architecture/0009-probabilistic-person-resolution.md)); the
  result carries the aggregated confidence.

## Data flow

```mermaid
flowchart LR
    ID["company / person id(s)"] --> Q["join / WITH RECURSIVE<br/>bounded + path array"] --> L["links (+confidence)"] --> API["API"]
```

## Inputs / outputs

- **Input:** a company or person id, optional hop limit / date filter.
- **Output:** linked companies/people with link type, period and confidence.

## Edge cases

- **Cycles** — prevented by the path array; never infinite-loop.
- **High fan-out** — bounded hop limit guards `work_mem`; document any truncation.
- **No NIF for persons** — person-based links remain candidates.
- **Performance ceiling** — if a query needs >3 unbounded hops or exceeds ~1 s / blows
  `work_mem`, escalate to Apache AGE per [ADR-0010](../architecture/0010-postgresql-ctes-over-graph-db.md).

## Acceptance criteria

- Shared-admin and shared-address queries return correct, deduplicated links with periods.
- Multi-hop traversal terminates with cycle detection and respects the hop limit.
- Person-derived links carry confidence and are never labelled as certain.

## Implementation issues

- [ ] Shared-administrator query (overlap-aware self-join) + confidence.
- [ ] Shared-registered-address query.
- [ ] Bounded multi-hop recursive CTE with path-array cycle detection.
- [ ] Confidence aggregation across a path.
- [ ] Query performance benchmarks + the AGE escalation decision gate.
- [ ] API endpoints exposing the above (with [read-api](read-api.md)).
