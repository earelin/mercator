# mercator

Turns Spain's official mercantile gazette (BORME) into a queryable graph of companies, people, and the links between them.

## Status

**Early implementation.** The fetch/parse/cache and persistence layers exist — the BOE HTTP
client (rate-limited, retrying), the disk document cache, the HTML/PDF/XML extractors, the
Sección A summary enumeration, and the Flyway baseline (`V1.0.0__baseline.sql`). The gated
historical-import endpoint is scaffolded (in-memory job store). Still to build: the ingestion
service that resolves and persists companies/people/links, the read-only query API, the daily
incremental scheduler, and the bulk historical-import engine.

## Repository structure

A **single-project Gradle 9.6** build (`settings.gradle.kts`, pinned via the wrapper) producing
one Java 25 + Micronaut server artifact. Layers are separated by **package**, not by Gradle
module — only the dependency *direction* is enforced (`domain ← infrastructure ← application`):

| Path | Component |
|------|-----------|
| [`docs/`](docs/) | Specs, features and architecture decisions (the design source of truth). |
| `src/…/domain/` | Framework-free domain/application core: model, ports, ingestion/parse/normalise logic. |
| `src/…/infrastructure/` | Driven adapters: BOE HTTP client, document cache, extractors, JDBC persistence. |
| `src/…/application/` | Micronaut driving adapters + wiring: the **read-only** API, the gated historical-import endpoint (`application.rest.admin.imports`), the daily incremental scheduler, and the `@Factory` composition beans. |

Start with [`docs/specs/`](docs/specs/README.md) for *what* the system does,
[`docs/features/`](docs/features/README.md) for *how*, and
[`docs/architecture/`](docs/architecture/README.md) for *why*.

## Building and running

```sh
docker compose up -d   # Postgres (required before run / integration / acceptance)
./gradlew run          # start the server
./gradlew test         # fast unit tests only (no Docker)
./gradlew check        # unit tests + Checkstyle, PMD, CPD, Error Prone/NullAway, SpotBugs/FindSecBugs
./gradlew integration  # controller + JDBC adapter tests (Testcontainers; needs Docker; not in check)
./gradlew acceptance   # black-box tests over the full Docker Compose stack (needs Docker; not in check/build)
./gradlew build        # compile + unit tests
```

## Development

`scripts/ci.sh` is the local CI pipeline. With no arguments it runs every check; pass names to
run only those (`./scripts/ci.sh markdown links`). The checks, in order:

| Check | What it does |
|-------|--------------|
| `markdown` | Markdown formatting/style (markdownlint-cli2) |
| `links` | Relative/anchor + external links (lychee) |
| `mermaid` | Mermaid diagram syntax (mmdc) |
| `compose` | Docker Compose lint (dclint via npx) |
| `check` | Gradle `check` — Checkstyle, PMD, CPD, Error Prone/NullAway, SpotBugs/FindSecBugs, tests |
| `build` | Gradle `build` |
| `sql` | SQL/Flyway migration lint (sqlfluff) |
| `openapi` | OpenAPI validate + security (Spectral: `spectral:oas` + OWASP ruleset) |
| `opengrep` | SAST security scan of `src/main` (opengrep) |
| `actions` | GitHub Actions workflow lint (actionlint) |

```sh
./scripts/ci.sh                    # full run (includes external link checks)
CHECK_EXTERNAL=0 ./scripts/ci.sh   # skip network/external link checks
./scripts/ci.sh --help             # list every check
```

It runs automatically before every push as a git pre-push hook. Enable it once per clone:

```sh
git config core.hooksPath .githooks
```

The heavyweight dynamic API check — driving a **running** server against the OpenAPI contract
for drift and security (Schemathesis) — is kept out of `ci.sh` and lives in its own script:

```sh
./scripts/api-conformance.sh       # needs the app running (./gradlew run)
```

It is configured via `MERCATOR_BASE_URL` (default `http://localhost:8080`), `MERCATOR_API_KEY`
(sent as `X-API-Key`) and `SCHEMATHESIS_MAX_EXAMPLES`.

### Required tools

`ci.sh` shells out to: [`markdownlint-cli2`](https://github.com/DavidAnson/markdownlint-cli2),
[`lychee`](https://github.com/lycheeverse/lychee),
[`@mermaid-js/mermaid-cli`](https://github.com/mermaid-js/mermaid-cli) (`mmdc`, needs a local
Chrome/Chromium — auto-detected, or set `PUPPETEER_EXECUTABLE_PATH`),
[`sqlfluff`](https://github.com/sqlfluff/sqlfluff),
[`opengrep`](https://github.com/opengrep/opengrep) and
[`actionlint`](https://github.com/rhysd/actionlint). The `compose` and `openapi` checks run
[`dclint`](https://github.com/zavoloklom/docker-compose-linter) and
[`spectral`](https://github.com/stoplightio/spectral) on demand via `npx`. The separate dynamic
conformance script uses [`schemathesis`](https://github.com/schemathesis/schemathesis)
(`pipx install schemathesis`).
