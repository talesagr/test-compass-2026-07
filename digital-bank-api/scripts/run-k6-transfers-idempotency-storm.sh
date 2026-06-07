#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export BASE_URL="${BASE_URL:-http://localhost:8080}"
export SHARED_IDEMPOTENCY_KEY="${SHARED_IDEMPOTENCY_KEY:-k6-storm-shared-key}"
exec k6 run "$ROOT/scripts/k6/03-transfers-idempotency-storm.js"
