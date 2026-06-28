# Acceptance WireMock stubs

Mounted read-only into the acceptance `wiremock` service at `/home/wiremock` (see
`docker/acceptance/compose.yaml`). WireMock simulates the external BORME/BOE HTTP services so the
acceptance run is deterministic and offline.

- `mappings/` — stub definitions (`*.json`): request matchers → canned responses.
- `__files/` — static response bodies referenced from a mapping via `"bodyFileName"`.

`example-borme-summary.json` is an illustrative placeholder; replace it with real stubs once the
ingestion engine's BORME/BOE URL shapes exist. The app reaches WireMock at `http://wiremock:8080`
(`MERCATOR_SOURCE_BOE_BASE_URL`).
