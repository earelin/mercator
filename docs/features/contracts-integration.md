# Feature — Contracts-project integration

## Summary

Let the sibling public-contracts project match its awardees to Mercator companies via a
shared normalised-name + province matcher, surfaced as scored candidates — read-only, with
no write coupling.

## Related specs / ADRs

- Specs: [5 — Link detection](../specs/05-link-detection.md)
- ADRs: [0009 — Probabilistic resolution](../architecture/0009-probabilistic-person-resolution.md), [0008 — Registry coordinates as key](../architecture/0008-registry-coordinates-as-company-natural-key.md)

## Functional behaviour

- The contracts project consumes Mercator **read-only** (via the read API or a read replica).
- **Matching key:** normalised company name + province, using the **same** normalisation
  function as ingestion ([entity-extraction-normalisation](entity-extraction-normalisation.md))
  and `pg_trgm` similarity.
- Because contracts data has **NIFs** but the BORME does **not**, the join cannot be on NIF.
  Mercator clusters companies and contract awardees by normalised name and presents **ranked
  candidate matches** with scores.
- Matches live in a dedicated **`match_candidate`** layer (with scores), keeping the two
  projects loosely coupled — no write coupling in either direction.

## Data flow

```mermaid
flowchart LR
  A["contract awardee<br/>name, province, NIF"] --> N["normalise"] --> T["trigram match vs company.norm_name"]
  T --> C["ranked candidates"] --> MC["match_candidate (score)"] --> X["contracts project consumes"]
```

## Inputs / outputs

- **Input:** contract awardee identifiers (name + province; NIF retained on the contracts
  side).
- **Output:** ranked Mercator company candidates with match scores in `match_candidate`.

## Edge cases

- **Name-only ambiguity** — multiple plausible companies; return ranked candidates, never a
  forced single match.
- **Normalisation drift** — both sides MUST share one normalisation definition, or matches
  silently degrade.
- **No match** — recorded as such; not an error.

## Acceptance criteria

- Awardees match to ranked company candidates using the shared normaliser.
- The contracts project never writes to Mercator; coupling is read-only.
- Scores are calibrated and exposed; ambiguous cases stay candidates.

## Implementation issues

- [ ] `match_candidate` table + scoring schema.
- [ ] Shared normaliser exposed/usable from the matcher (parity with ingestion).
- [ ] Name+province trigram matcher producing ranked candidates.
- [ ] Read-only access path for the contracts project (API or read replica).
- [ ] Match-quality evaluation against a labelled sample.
