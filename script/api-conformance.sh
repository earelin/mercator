#!/usr/bin/env bash
#
# OpenAPI <-> implementation conformance + security (schemathesis).
#
# Heavyweight, dynamic check kept OUT of script/ci.sh: it drives a *running* server with
# property-based cases generated from docs/specs/api.openapi.yaml and asserts the responses
# conform — drift checks (status_code / response_schema / content_type /
# response_headers_conformance) and security checks (negative_data_rejection — malformed
# input must be rejected, mirroring additionalProperties: false; ignored_auth — endpoints
# that should require the key must enforce it, per ADR-0013). Schemathesis runs its full
# check suite by default.
#
# Each operation is resolved under its declared server (/api/v1 for reads, / for the admin
# overrides), so $BASE_URL is just scheme+host.
#
# Run it manually:   ./script/api-conformance.sh
#   server URL:      MERCATOR_BASE_URL (default http://localhost:8080)
#   auth:            MERCATOR_API_KEY  (sent as X-API-Key; admin endpoints need it, ADR-0013)
#   case budget:     SCHEMATHESIS_MAX_EXAMPLES (default 25, per operation)
#
# Needs a reachable server; exits non-zero with guidance if none answers at $BASE_URL.
#
set -uo pipefail

cd "$(dirname "$0")/.."

bold() { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }
ok()   { printf '\033[32m✓ %s\033[0m\n' "$1"; }
err()  { printf '\033[31m✗ %s\033[0m\n' "$1" >&2; }

bold "OpenAPI conformance + security (schemathesis)"
OPENAPI_FILE="docs/specs/api.openapi.yaml"
BASE_URL="${MERCATOR_BASE_URL:-http://localhost:8080}"

if [ ! -f "$OPENAPI_FILE" ]; then
  err "OpenAPI doc not found: $OPENAPI_FILE"; exit 1
fi

if ! command -v curl >/dev/null 2>&1 \
   || ! curl -fsS -o /dev/null --max-time 3 "$BASE_URL/api/v1/health/liveness" 2>/dev/null; then
  err "no server reachable at $BASE_URL — start the app (./gradlew run) then re-run, or set MERCATOR_BASE_URL"
  exit 1
fi

if ! command -v st >/dev/null 2>&1 && ! command -v schemathesis >/dev/null 2>&1; then
  err "schemathesis not found — install: pipx install schemathesis (or: pip install schemathesis)"
  exit 1
fi

ST_BIN="$(command -v st || command -v schemathesis)"
# All checks are enabled by default; --max-examples bounds generation per operation.
st_args=(run "$OPENAPI_FILE" --url "$BASE_URL" \
         --max-examples "${SCHEMATHESIS_MAX_EXAMPLES:-25}")
# The admin endpoints require X-API-Key in every environment (ADR-0013); pass it when set.
[ -n "${MERCATOR_API_KEY:-}" ] && st_args+=(--header "X-API-Key: ${MERCATOR_API_KEY}")

if "$ST_BIN" "${st_args[@]}"; then
  ok "OpenAPI conformance + security"
else
  err "schemathesis found contract drift or security issues"; exit 1
fi
