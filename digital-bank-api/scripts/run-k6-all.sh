#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"$DIR/run-k6-accounts.sh"
"$DIR/run-k6-transfers-concurrent.sh"
"$DIR/run-k6-transfers-idempotency-storm.sh"
"$DIR/run-k6-transfers-pingpong.sh"
