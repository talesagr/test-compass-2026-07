#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export BASE_URL="${BASE_URL:-http://localhost:8080}"
exec k6 run "$ROOT/scripts/k6/01-accounts-paging.js"
