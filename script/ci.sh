#!/usr/bin/env bash
#
# Local CI pipeline.
# Verifies, for every Markdown file in the repo:
#   1. Markdown formatting/style   -> markdownlint-cli2
#   2. Links (relative + anchors, and external URLs) -> lychee
#   3. Mermaid diagram syntax      -> @mermaid-js/mermaid-cli (mmdc)
#   4. Docker Compose files        -> dclint (via npx)
# And then:
#   5. Gradle build                -> ./gradlew build
#   6. SQL lint                    -> sqlfluff lint
#
# Run it manually:        ./script/ci.sh
# Skip external links:    CHECK_EXTERNAL=0 ./script/ci.sh
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

# --- 5) Gradle build ------------------------------------------------------
bold "Gradle build (./gradlew build)"
if [ -f ./gradlew ]; then
  if ./gradlew build; then ok "gradle build"; else err "gradle build failed"; fail=1; fi
else
  err "gradlew not found — run: gradle wrapper --gradle-version 9.5.1"; fail=1
fi

# --- 6) SQL lint ----------------------------------------------------------
bold "SQL lint (sqlfluff)"
SQL_DIR="server/src/main/resources/db/migration"
if command -v sqlfluff >/dev/null 2>&1; then
  if sqlfluff lint "$SQL_DIR"; then ok "SQL lint"; else err "SQL lint issues"; fail=1; fi
else
  err "sqlfluff not found — install: pip install sqlfluff (or: brew install sqlfluff)"; fail=1
fi

# --- Result ---------------------------------------------------------------
echo
if [ "$fail" -eq 0 ]; then printf '\033[32mAll checks passed.\033[0m\n'; else
  printf '\033[31mChecks FAILED.\033[0m\n'; fi
exit "$fail"
