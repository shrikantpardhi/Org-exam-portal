#!/usr/bin/env bash
# End-to-end browser smoke test for the Docker Compose stack (GAP-015 / GAP-137).
#
# Loads the frontend through the Nginx edge in a real headless browser, captures every console
# message and failed network request, then drives a login through the real UI and asserts that the
# API call reaches the backend. This is the check that distinguishes "the page renders" from
# "the application actually works", which unit tests and curl cannot do.
#
# Requirements: bash, curl, node, and a Chrome/Chromium binary (set CHROME_BIN if not auto-found).
# Usage:
#   ./scripts/smoke-frontend.sh                      # edge on http://localhost:8081
#   EDGE_URL=http://localhost:8080 ./scripts/smoke-frontend.sh
#   SMOKE_EMAIL=student@example.com SMOKE_PASSWORD=... ./scripts/smoke-frontend.sh
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

EDGE_URL="${EDGE_URL:-http://localhost:${EDGE_HTTP_PORT:-8081}}"
SMOKE_EMAIL="${SMOKE_EMAIL:-}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-}"
ARTIFACT_DIR="${SMOKE_ARTIFACT_DIR:-${ROOT_DIR}/.dsh/tmp/smoke}"

pass() { printf '  \033[32mPASS\033[0m %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; }
info() { printf '\033[36m==>\033[0m %s\n' "$1"; }

# A candidate is usable if it exists as a file. `-x` is not reliable for Windows paths reached
# through Git Bash / MSYS, where the executable bit is synthesized rather than stored.
find_chrome() {
    if [ -n "${CHROME_BIN:-}" ] && [ -e "${CHROME_BIN}" ]; then echo "${CHROME_BIN}"; return; fi
    for candidate in \
        "/c/Program Files/Google/Chrome/Application/chrome.exe" \
        "/c/Program Files (x86)/Google/Chrome/Application/chrome.exe" \
        "/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" \
        "/c/Program Files/Microsoft/Edge/Application/msedge.exe" \
        "/usr/bin/google-chrome" "/usr/bin/chromium" "/usr/bin/chromium-browser" \
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
    do
        [ -e "${candidate}" ] && { echo "${candidate}"; return; }
    done
    command -v google-chrome 2>/dev/null || command -v chromium 2>/dev/null || true
}

CHROME_BIN="$(find_chrome)"
if [ -z "${CHROME_BIN}" ]; then
    echo "No Chrome/Chromium binary found. Set CHROME_BIN to one." >&2
    exit 2
fi
if ! command -v node >/dev/null 2>&1; then
    echo "node is required to drive the browser over the DevTools protocol." >&2
    exit 2
fi

mkdir -p "${ARTIFACT_DIR}"

info "Edge under test: ${EDGE_URL}"
if ! curl -sf -o /dev/null --max-time 10 "${EDGE_URL}/"; then
    fail "frontend is not reachable at ${EDGE_URL}"
    exit 1
fi
pass "frontend is reachable at the edge"

info "Driving a real browser"
node "${SCRIPT_DIR}/smoke-frontend.mjs" \
    --chrome "${CHROME_BIN}" \
    --url "${EDGE_URL}" \
    --artifacts "${ARTIFACT_DIR}" \
    --email "${SMOKE_EMAIL}" \
    --password "${SMOKE_PASSWORD}"
status=$?

if [ "$status" -eq 0 ]; then
    pass "browser smoke test passed (no console errors, no blocked API calls)"
else
    fail "browser smoke test failed (see ${ARTIFACT_DIR})"
fi
exit "$status"
