# mercator

Turns Spain's official mercantile gazette (BORME) into a queryable graph of companies, people, and the links between them.

## Repository structure

A **single-project Gradle 9.5** build (`settings.gradle.kts`) producing one Java 25 + Micronaut
server artifact, with layers separated by package under `src/`:

| Path | Component |
|------|-----------|
| [`docs/`](docs/) | Specs, features and architecture decisions (the design source of truth). |
| `src/…/domain/` | Framework-free domain/application core: model, ports, ingestion/parse/normalise logic. |
| `src/…/infrastructure/` | Driven adapters: BOE HTTP client, document cache, extractors, JDBC persistence. |
| `src/…/server/` | Micronaut driving adapters + wiring: the **read-only** API, the daily-incremental scheduler, and the gated historical-import endpoint. |

Start with [`docs/specs/`](docs/specs/README.md) for *what* the system does,
[`docs/features/`](docs/features/README.md) for *how*, and
[`docs/architecture/`](docs/architecture/README.md) for *why*.

> Early implementation: the document-fetch/parse/persistence layers and the full Flyway schema
> exist; the ingestion service, resolution functions, read API and the historical-import engine
> are still to be built (the import endpoint is scaffolded).

## Development

`script/ci.sh` is the local CI pipeline. It checks every Markdown file for formatting,
links (relative paths, heading anchors and external URLs) and Mermaid diagram syntax;
builds and tests the code; lints the SQL; and statically validates the OpenAPI contract
(Spectral — structure + OWASP security):

```sh
./script/ci.sh                    # full run (includes external link checks)
CHECK_EXTERNAL=0 ./script/ci.sh   # skip network/external link checks
```

It runs automatically before every push as a git pre-push hook. Enable it once per clone:

```sh
git config core.hooksPath .githooks
```

The heavyweight dynamic API check — driving a **running** server against the OpenAPI contract
for drift and security (Schemathesis) — is kept out of `ci.sh` and lives in its own script:

```sh
./script/api-conformance.sh       # needs the app running (./gradlew run)
```

It is configured via `MERCATOR_BASE_URL` (default `http://localhost:8080`), `MERCATOR_API_KEY`
(sent as `X-API-Key`) and `SCHEMATHESIS_MAX_EXAMPLES`.

Required tools: [`markdownlint-cli2`](https://github.com/DavidAnson/markdownlint-cli2),
[`lychee`](https://github.com/lycheeverse/lychee) and
[`@mermaid-js/mermaid-cli`](https://github.com/mermaid-js/mermaid-cli) (`mmdc`) — the last
needs a local Chrome/Chromium (auto-detected, or set `PUPPETEER_EXECUTABLE_PATH`).
The OpenAPI checks additionally use [`spectral`](https://github.com/stoplightio/spectral) (run
on demand via `npx`, in `ci.sh`) and, for the separate dynamic conformance script,
[`schemathesis`](https://github.com/schemathesis/schemathesis) (`pipx install schemathesis`).
