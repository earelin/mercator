#!/usr/bin/env bash
#
# Local CI pipeline.
# Verifies, for every Markdown file in the repo:
#   1. Markdown formatting/style   -> markdownlint-cli2
#   2. Links (relative + anchors, and external URLs) -> lychee
#   3. Mermaid diagram syntax      -> @mermaid-js/mermaid-cli (mmdc)
#   4. Docker Compose files        -> dclint (via npx)
# And then:
#   5. Gradle check (Checkstyle, CPD/duplication, tests) -> ./gradlew check
#   6. Gradle build                -> ./gradlew build
#   7. SQL lint                    -> sqlfluff lint
#   8. OpenAPI validate + security -> spectral (spectral:oas + OWASP ruleset)
#   9. OpenAPI <-> impl conformance + security -> schemathesis (drift + security)
#
# Step 8 is static (lints the contract document); step 9 is dynamic — it drives a running
# server with cases generated from the contract to catch drift (status/schema/content-type/
# header conformance) and security issues (e.g. ignored_auth, negative_data_rejection). It
# needs a reachable server, so it auto-skips when none is up (e.g. while the API is still
# being built).
#
# Run it manually:        ./script/ci.sh
# Skip external links:    CHECK_EXTERNAL=0 ./script/ci.sh
# API conformance:        schemathesis runs only when a server answers at $MERCATOR_BASE_URL
#                         (default http://localhost:8080); force/skip with
#                         RUN_SCHEMATHESIS=1/0; auth via MERCATOR_API_KEY.
# It also runs automatically as a git pre-push hook (see .githooks/pre-push;
# enable with: git config core.hooksPath .githooks).
#
set -uo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

bold() { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }
ok()   { printf '\033[32m✓ %s\033[0m\n' "$1"; }
err()  { printf '\033[31m✗ %s\033[0m\n' "$1" >&2; }
fail=0

# --- Collect Markdown files (working tree), excluding generated/vendor dirs ---
mapfile -t MD_FILES < <(find . \
  -type d \( -name .git -o -name node_modules -o -name build -o -name .gradle \
             -o -name .claude -o -path ./.mermaid/tmp \) -prune -o \
  -type f -name '*.md' -print | sed 's#^\./##' | sort)

if [ "${#MD_FILES[@]}" -eq 0 ]; then echo "No Markdown files found."; exit 0; fi
printf 'Checking %d Markdown files.\n' "${#MD_FILES[@]}"

# --- 1) Markdown format ---------------------------------------------------
bold "Markdown lint (markdownlint-cli2)"
if command -v markdownlint-cli2 >/dev/null 2>&1; then
  if markdownlint-cli2 "${MD_FILES[@]}"; then ok "markdown format"; else err "markdown format issues"; fail=1; fi
else
  err "markdownlint-cli2 not found — install: npm i -g markdownlint-cli2"; fail=1
fi

# --- 2) Links -------------------------------------------------------------
bold "Links (lychee)"
if command -v lychee >/dev/null 2>&1; then
  echo "- internal: relative paths + heading anchors (offline)"
  if lychee --offline --include-fragments --no-progress "${MD_FILES[@]}"; then
    ok "internal links + anchors"
  else err "broken internal links/anchors"; fail=1; fi

  if [ "${CHECK_EXTERNAL:-1}" = "1" ]; then
    echo "- external: URLs over the network (set CHECK_EXTERNAL=0 to skip)"
    if lychee --cache --max-retries 2 --max-concurrency 4 --accept '200,206,429' \
              --no-progress "${MD_FILES[@]}"; then
      ok "external links"
    else err "broken external links (or network/rate-limit — re-run, or CHECK_EXTERNAL=0)"; fail=1; fi
  else
    echo "  (skipped — CHECK_EXTERNAL=0)"
  fi
else
  err "lychee not found — install: cargo install lychee (or: brew install lychee)"; fail=1
fi

# --- 3) Mermaid diagrams --------------------------------------------------
bold "Mermaid diagrams (mmdc)"
MMD_FILES=()
for f in "${MD_FILES[@]}"; do grep -q '```mermaid' "$f" && MMD_FILES+=("$f"); done

if [ "${#MMD_FILES[@]}" -eq 0 ]; then
  ok "no mermaid diagrams to validate"
elif command -v mmdc >/dev/null 2>&1; then
  out="$ROOT/.mermaid/tmp"; mkdir -p "$out"
  trap 'rm -rf "$out"' EXIT
  # mermaid-cli uses puppeteer-core, which ships no browser — point it at a system Chrome/Chromium.
  CHROME="${PUPPETEER_EXECUTABLE_PATH:-}"
  if [ -z "$CHROME" ]; then
    for b in google-chrome google-chrome-stable chromium chromium-browser chrome-headless-shell; do
      if command -v "$b" >/dev/null 2>&1; then CHROME="$(command -v "$b")"; break; fi
    done
  fi
  pcfg="$out/puppeteer.json"
  if [ -n "$CHROME" ]; then
    printf '{ "executablePath": "%s", "args": ["--no-sandbox", "--disable-setuid-sandbox"] }\n' "$CHROME" > "$pcfg"
  else
    err "no Chrome/Chromium found for Mermaid rendering — install one (e.g. 'npx puppeteer browsers install chrome') or set PUPPETEER_EXECUTABLE_PATH"
    cp "$ROOT/.mermaid/puppeteer.json" "$pcfg"
  fi
  for f in "${MMD_FILES[@]}"; do
    safe="${f//\//_}"
    if mmdc -i "$f" -o "$out/${safe}" -p "$pcfg" --quiet >/dev/null 2>"$out/err.log"; then
      ok "mermaid: $f"
    else
      err "invalid mermaid in $f"; sed 's/^/    /' "$out/err.log" >&2; fail=1
    fi
  done
else
  err "mmdc not found — install: npm i -g @mermaid-js/mermaid-cli"; fail=1
fi

# --- 4) Docker Compose lint -----------------------------------------------
bold "Docker Compose lint (dclint)"
if command -v npx >/dev/null 2>&1; then
  # require-quotes-in-ports: false positive — dclint misidentifies already-quoted ports as unquoted
  # no-unbound-port-interfaces: intentionally not binding to 127.0.0.1
  if npx dclint .; then ok "docker-compose files"; else err "docker-compose lint issues"; fail=1; fi
else
  err "npx not found"; fail=1
fi

# --- 5) Gradle check (Checkstyle, CPD/duplication, tests) -----------------
bold "Gradle check (./gradlew check)"
if [ -f ./gradlew ]; then
  if ./gradlew check; then ok "gradle check"; else err "gradle check failed (checkstyle/CPD/tests)"; fail=1; fi
else
  err "gradlew not found — run: gradle wrapper --gradle-version 9.5.1"; fail=1
fi

# --- 6) Gradle build ------------------------------------------------------
bold "Gradle build (./gradlew build)"
if [ -f ./gradlew ]; then
  if ./gradlew build; then ok "gradle build"; else err "gradle build failed"; fail=1; fi
else
  err "gradlew not found — run: gradle wrapper --gradle-version 9.5.1"; fail=1
fi

# --- 7) SQL lint ----------------------------------------------------------
bold "SQL lint (sqlfluff)"
SQL_DIR="src/main/resources/db/migration"
if command -v sqlfluff >/dev/null 2>&1; then
  if sqlfluff lint "$SQL_DIR"; then ok "SQL lint"; else err "SQL lint issues"; fail=1; fi
else
  err "sqlfluff not found — install: pip install sqlfluff (or: brew install sqlfluff)"; fail=1
fi

# --- 8) OpenAPI validate + security (spectral) ----------------------------
bold "OpenAPI lint + security (spectral)"
OPENAPI_FILE="docs/specs/api.openapi.yaml"
if [ ! -f "$OPENAPI_FILE" ]; then
  err "OpenAPI doc not found: $OPENAPI_FILE"; fail=1
elif command -v npx >/dev/null 2>&1; then
  # spectral:oas = structural validation; spectral-owasp-ruleset = API security checks.
  # Both packages are passed to npx so the .spectral.yaml `extends` resolve (no package.json).
  if npx --yes --package=@stoplight/spectral-cli --package=@stoplight/spectral-owasp-ruleset \
       spectral lint "$OPENAPI_FILE" --ruleset .spectral.yaml --fail-severity=warn; then
    ok "OpenAPI lint + security"
  else
    err "OpenAPI lint/security issues"; fail=1
  fi
else
  err "npx not found"; fail=1
fi

# --- 9) OpenAPI <-> implementation conformance + security (schemathesis) ---
# Property-based testing of the *running* API against the contract: generates requests from
# docs/specs/api.openapi.yaml and asserts the responses conform (drift) while probing for
# security issues. Schemathesis runs its full check suite by default — the conformance checks
# (status_code/response_schema/content_type/response_headers_conformance) and the security ones
# (negative_data_rejection — malformed input must be rejected, mirroring additionalProperties:
# false; ignored_auth — endpoints that should require the key must enforce it, per ADR-0013).
# Needs a reachable server, so it auto-skips when none answers at $BASE_URL — the API is still
# being built. Each operation is resolved under its declared server (/api/v1 for reads, / for
# the admin overrides), so $BASE_URL is just scheme+host.
bold "OpenAPI conformance + security (schemathesis)"
OPENAPI_FILE="docs/specs/api.openapi.yaml"
BASE_URL="${MERCATOR_BASE_URL:-http://localhost:8080}"
RUN_ST="${RUN_SCHEMATHESIS:-auto}"

reachable=0
if command -v curl >/dev/null 2>&1 \
   && curl -fsS -o /dev/null --max-time 3 "$BASE_URL/api/v1/health/liveness" 2>/dev/null; then
  reachable=1
fi

if [ ! -f "$OPENAPI_FILE" ]; then
  err "OpenAPI doc not found: $OPENAPI_FILE"; fail=1
elif [ "$RUN_ST" = "0" ]; then
  echo "  (skipped — RUN_SCHEMATHESIS=0)"
elif [ "$RUN_ST" != "1" ] && [ "$reachable" -eq 0 ]; then
  echo "  (skipped — no server at $BASE_URL; start it and re-run, or set RUN_SCHEMATHESIS=1)"
elif ! command -v st >/dev/null 2>&1 && ! command -v schemathesis >/dev/null 2>&1; then
  err "schemathesis not found — install: pipx install schemathesis (or: pip install schemathesis)"; fail=1
else
  ST_BIN="$(command -v st || command -v schemathesis)"
  # All checks are enabled by default; --max-examples bounds generation per operation for CI.
  st_args=(run "$OPENAPI_FILE" --url "$BASE_URL" \
           --max-examples "${SCHEMATHESIS_MAX_EXAMPLES:-25}")
  # The admin endpoints require X-API-Key in every environment (ADR-0013); pass it when set.
  [ -n "${MERCATOR_API_KEY:-}" ] && st_args+=(--header "X-API-Key: ${MERCATOR_API_KEY}")
  if "$ST_BIN" "${st_args[@]}"; then
    ok "OpenAPI conformance + security"
  else
    err "schemathesis found contract drift or security issues"; fail=1
  fi
fi

# --- Result ---------------------------------------------------------------
echo
if [ "$fail" -eq 0 ]; then printf '\033[32mAll checks passed.\033[0m\n'; else
  printf '\033[31mChecks FAILED.\033[0m\n'; fi
exit "$fail"
