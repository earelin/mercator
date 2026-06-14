# Spec 5 — Link detection

**Realised by:** [link-queries](../features/link-queries.md),
[entity-resolution](../features/entity-resolution.md),
[contracts-integration](../features/contracts-integration.md).
**Constrained by:** [ADR-0010](../architecture/0010-postgresql-ctes-over-graph-db.md),
[ADR-0009](../architecture/0009-probabilistic-person-resolution.md).

## What this describes

The relationships Mercator computes between companies and people, and how confident it is
about each.

## Links answered

1. **Shared administrator.** Two companies are linked if the same resolved person holds (or
   ever held) an administrative role in both. Each appointment keeps its **full history**
   (temporal `appointment` intervals), so the match spans current *and* past roles by
   default; it can optionally be constrained to overlapping validity (people who held the
   roles at the same time), in which case the overlapping period is returned. Returns the
   companies and the shared person.
2. **Shared registered address.** Two companies are linked if they share the same
   normalised registered address. Each company keeps its **full address history**
   (temporal `company_address` intervals), so the match spans current *and* past domiciles by
   default; it can optionally be constrained to overlapping validity (co-located at the same
   time).
3. **Multi-hop connection.** "Companies connected through people" — bounded traversal of
   the company⇄person graph (e.g. companies 2–3 hops away through shared individuals).
   Traversal carries a path and detects cycles.

## Confidence

- **Company-to-company via shared address** and **via a company whose identity is keyed by
  Hoja+province** are high-confidence (deterministic keys).
- **Anything routed through a person is a scored candidate**, because persons have no
  identifier (see [Spec 4](04-data-model.md)). Mercator attaches a confidence score and
  never presents a person-based link as an established fact — both to avoid conflating
  individuals and to meet the GDPR accuracy obligation
  ([Spec 7](07-data-protection.md)).
- **Historical vs. current matches.** Because shared-administrator and shared-address links
  span the full history by default, each link is reported with whether it is a
  **current/overlapping** match (shared at the same time) or a **historical** one (connected
  only across non-overlapping intervals). A historical match **carries lower confidence** — it
  is weaker evidence of an active relationship — and this temporal flag is surfaced alongside
  the link so a past coincidence is never reported as a live connection.

## Boundaries

- Default traversals are **bounded** (small hop limit) and run as indexed joins / recursive
  CTEs inside PostgreSQL ([ADR-0010](../architecture/0010-postgresql-ctes-over-graph-db.md)).
- Deep, unbounded, Cypher-style traversal is **not** an initial guarantee; if it becomes
  necessary, the graph extension Apache AGE is the planned escalation, not a second
  datastore.

## Relationship to the contracts project

The public-contracts project consumes these links **read-only**. Its own awardees are
matched to Mercator companies by normalised name + province (it has NIFs, the BORME does
not, so the join is name-based and ranked, not exact). Matches are surfaced as scored
candidates in a dedicated matching layer, keeping the two projects loosely coupled (see
[contracts-integration](../features/contracts-integration.md)).
