#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -z "${HORSE_E2E_RUNTIME_DIR:-}" ]]; then
  exec "$root_dir/backend/gradlew" -p "$root_dir/backend" \
    bootRun --args="--server.address=127.0.0.1 --server.port=${HORSE_E2E_BACKEND_PORT:-8080}"
fi

: "${HORSE_E2E_BACKEND_PORT:?HORSE_E2E_BACKEND_PORT is required with HORSE_E2E_RUNTIME_DIR}"

exec "$root_dir/backend/gradlew" -p "$root_dir/backend" --no-daemon \
  --project-cache-dir "$HORSE_E2E_RUNTIME_DIR/gradle-project-cache" \
  -PisolatedBuildDir="$HORSE_E2E_RUNTIME_DIR/backend-build" \
  bootRun --args="--server.address=127.0.0.1 --server.port=$HORSE_E2E_BACKEND_PORT"
