# mercator

Turns Spain's official mercantile gazette (BORME) into a queryable graph of companies, people, and the links between them.

## Repository structure

A **Gradle 9.5 multi-project** (`settings.gradle.kts`) with three subprojects:

| Path | Component |
|------|-----------|
| [`docs/`](docs/) | Specs, features and architecture decisions (the design source of truth). |
| [`shared/`](shared/) | Common Java library (BOE client, parser, normalisation, ingestion service) used by both `server` and `ingester`. |
| [`server/`](server/) | Java 25 + Micronaut **read-only API** + the daily-incremental scheduler — the only server-hosted component. |
| [`ingester/`](ingester/) | Java offline CLI for the historical backfill — runs locally/off-server. |

Start with [`docs/specs/`](docs/specs/README.md) for *what* the system does,
[`docs/features/`](docs/features/README.md) for *how*, and
[`docs/architecture/`](docs/architecture/README.md) for *why*.

> The project is in its initial (documentation) phase — `shared/`, `server/` and `ingester/`
> are scaffolding placeholders with no code yet.

## Development

`script/ci.sh` is the local CI pipeline. It checks every Markdown file for formatting,
links (relative paths, heading anchors and external URLs) and Mermaid diagram syntax:

```sh
./script/ci.sh                    # full run (includes external link checks)
CHECK_EXTERNAL=0 ./script/ci.sh   # skip network/external link checks
```

It runs automatically before every push as a git pre-push hook. Enable it once per clone:

```sh
git config core.hooksPath .githooks
```

Required tools: [`markdownlint-cli2`](https://github.com/DavidAnson/markdownlint-cli2),
[`lychee`](https://github.com/lycheeverse/lychee) and
[`@mermaid-js/mermaid-cli`](https://github.com/mermaid-js/mermaid-cli) (`mmdc`) — the last
needs a local Chrome/Chromium (auto-detected, or set `PUPPETEER_EXECUTABLE_PATH`).
