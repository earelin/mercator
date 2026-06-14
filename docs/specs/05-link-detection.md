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

Every link is reported along **two orthogonal dimensions** that are **never conflated**:
*identity confidence* (are the entities correctly resolved?) and *temporal relevance* (was the
connection contemporaneous?). Keeping them separate is what removes the contradiction of a
"deterministic" link being assigned a sub-1.0 score.

**1. Identity confidence — `confidence ∈ [0.0, 1.0]`.** How sure Mercator is that the
*entities* on the link are correctly identified.

- **Deterministic-key matches have confidence `1.0`:** company identity keyed by Hoja+province
  ([ADR-0008](../architecture/0008-registry-coordinates-as-company-natural-key.md)) and the
  shared-address link keyed by exact normalised text + province (see [Spec 4](04-data-model.md)).
- **Anything routed through a person inherits the person's resolution confidence** — a scored
  value, never 1.0 by construction, because persons have no identifier (see
  [Spec 4](04-data-model.md), [ADR-0009](../architecture/0009-probabilistic-person-resolution.md)).
  Exact normalised-name + strong corroboration scores near the top of the range; weaker
  trigram/fuzzy matches score lower. Links below a **configurable floor (default ≈0.7)** are
  withheld. Mercator never presents a person-based link as an established fact — both to avoid
  conflating individuals and to meet the GDPR accuracy obligation ([Spec 7](07-data-protection.md)).
- **Multi-hop:** a path's identity confidence is the **product of its per-hop identity
  confidences** (independence assumption; deterministic hops contribute `1.0`), so longer or
  weaker chains score monotonically lower. The per-hop scores are returned with the path so a
  consumer can see *why* a chain is weak.

**2. Temporal relevance — a `temporal` flag, kept apart from identity confidence.** Because
shared-administrator and shared-address links span the full history by default, each link is
tagged **current/overlapping** (the parties shared the person/address at the same time) or
**historical** (connected only across non-overlapping intervals). A historical match is weaker
*evidence of an active relationship*, but that is a recency signal, **not** an identity doubt:
a deterministic shared-address link stays `confidence = 1.0` even when historical. The temporal
flag — plus an optional, clearly-labelled **recency weight** — carries that signal, and the
consumer decides how to weight recency. A past coincidence is therefore never reported as a
live connection, and a certain identity is never mislabelled as uncertain.

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
