#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
test_dir="$(mktemp -d "${TMPDIR:-/tmp}/horse-m31-16.XXXXXX")"
runtime_dir="$test_dir/runtime"
log_dir="$test_dir/logs"
mkdir -p "$runtime_dir" "$log_dir"
export TMPDIR="$runtime_dir"

background_pids=""
demo_proxy_pid=""
current_step='initialization'

cleanup() {
  for pid in $background_pids; do
    if kill -0 "$pid" 2>/dev/null; then
      if kill -TERM "$pid" 2>/dev/null; then
        if ! wait "$pid" 2>/dev/null; then
          : # A signal-owned child is expected to exit non-zero.
        fi
      fi
    fi
  done
  if [[ -n "$demo_proxy_pid" ]] && kill -0 "$demo_proxy_pid" 2>/dev/null; then
    if kill -TERM "$demo_proxy_pid" 2>/dev/null; then
      if ! wait "$demo_proxy_pid" 2>/dev/null; then
        : # The owned demo proxy exits because of SIGTERM.
      fi
    fi
  fi
  rm -rf "$test_dir"
  if [[ -e "$test_dir" ]]; then
    echo "Failed to remove harness runtime directory: $test_dir" >&2
    return 1
  fi
}

report_failure() {
  local line_number="$1"
  local exit_code="$2"
  echo "M31-16 harness isolation failed during: $current_step (line $line_number)" >&2
  echo "Diagnostic logs: $log_dir" >&2
  for log_file in "$log_dir"/*.log; do
    [[ -f "$log_file" ]] || continue
    echo "--- ${log_file##*/} ---" >&2
    tail -n 20 "$log_file" >&2
  done
  return "$exit_code"
}

trap 'report_failure "$LINENO" "$?"' ERR
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

wait_for_file() {
  local file="$1"
  local owner_pid="$2"
  for _ in {1..600}; do
    [[ -f "$file" ]] && return 0
    if ! kill -0 "$owner_pid" 2>/dev/null; then
      echo "Process $owner_pid exited before creating $file." >&2
      return 1
    fi
    sleep 0.1
  done
  echo "Timed out waiting for $file." >&2
  return 1
}

wait_for_port() {
  local port="$1"
  local owner_pid="$2"
  for _ in {1..300}; do
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1 && return 0
    if ! kill -0 "$owner_pid" 2>/dev/null; then
      echo "Process $owner_pid exited before opening port $port." >&2
      return 1
    fi
    sleep 0.1
  done
  echo "Timed out waiting for port $port." >&2
  return 1
}

wait_success() {
  local pid="$1"
  local label="$2"
  local log_file="$3"
  if ! wait "$pid"; then
    echo "$label failed. Output:" >&2
    cat "$log_file" >&2
    return 1
  fi
}

listener_pids() {
  local listeners
  if ! listeners="$(lsof -nP -t -iTCP:"$1" -sTCP:LISTEN 2>/dev/null)"; then
    return 0
  fi
  sort -u <<<"$listeners"
}

list_harness_databases() {
  docker exec horse-mysql sh -c \
    'exec env MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root --batch --skip-column-names --execute="SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME LIKE '\''horse_e2e_%'\'' OR SCHEMA_NAME LIKE '\''horse_openapi_%'\'' ORDER BY SCHEMA_NAME"'
}

find_database() {
  local database_name="$1"
  if [[ ! "$database_name" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo 'Harness database name contains unsupported characters.' >&2
    return 1
  fi
  printf "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '%s';\n" \
    "$database_name" \
    | docker exec -i horse-mysql sh -c \
      'exec env MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root --batch --skip-column-names'
}

cd "$root_dir"
status_before="$(git status --porcelain=v1 --untracked-files=all)"
current_step='database startup'
mise run db:up
databases_before="$(list_harness_databases)"

current_step='demo server coexistence with api:check'
existing_8080_pids="$(listener_pids 8080)"
if [[ -z "$existing_8080_pids" ]]; then
  JWT_SECRET='m31-16-demo-proxy-test-secret-more-than-32-bytes' \
    DEMO_PROXY_PORT=8080 DEMO_BACKEND_PORT=65534 \
    node scripts/local-demo-proxy.mjs >"$log_dir/demo-proxy.log" 2>&1 &
  demo_proxy_pid=$!
  wait_for_port 8080 "$demo_proxy_pid"
  existing_8080_pids="$demo_proxy_pid"
fi

mise run api:check >"$log_dir/api-with-demo.log" 2>&1
for pid in $existing_8080_pids; do
  kill -0 "$pid"
done

current_step='parallel api:check and E2E'
mise run api:check >"$log_dir/api-parallel-e2e.log" 2>&1 &
api_pid=$!
background_pids="$background_pids $api_pid"
pnpm --dir frontend/apps/web test:e2e -- tests/e2e/mvp3-reservation-lifecycle.spec.ts \
  >"$log_dir/e2e-parallel-api.log" 2>&1 &
e2e_pid=$!
background_pids="$background_pids $e2e_pid"
pnpm --dir frontend/apps/web test:e2e -- tests/e2e/m31-r13-responsive.spec.ts \
  >"$log_dir/e2e-same-task.log" 2>&1 &
same_e2e_pid=$!
background_pids="$background_pids $same_e2e_pid"
wait_success "$api_pid" 'parallel api:check' "$log_dir/api-parallel-e2e.log"
wait_success "$e2e_pid" 'parallel E2E' "$log_dir/e2e-parallel-api.log"
wait_success "$same_e2e_pid" 'second concurrent E2E' "$log_dir/e2e-same-task.log"
background_pids=""

current_step='parallel backend verification'
./scripts/run-isolated-gradle.sh test --tests '*HorseBackendApplicationTests' \
  >"$log_dir/backend-one.log" 2>&1 &
backend_one_pid=$!
background_pids="$background_pids $backend_one_pid"
./scripts/run-isolated-gradle.sh test --tests '*HorseBackendApplicationTests' \
  >"$log_dir/backend-two.log" 2>&1 &
backend_two_pid=$!
background_pids="$background_pids $backend_two_pid"
wait_success "$backend_one_pid" 'first backend verification' "$log_dir/backend-one.log"
wait_success "$backend_two_pid" 'second backend verification' "$log_dir/backend-two.log"
background_pids=""

current_step='parallel web build and E2E'
mise run web:build >"$log_dir/web-build.log" 2>&1 &
web_build_pid=$!
background_pids="$background_pids $web_build_pid"
pnpm --dir frontend/apps/web test:e2e -- tests/e2e/m31-r13-responsive.spec.ts \
  >"$log_dir/e2e-parallel-web.log" 2>&1 &
web_e2e_pid=$!
background_pids="$background_pids $web_e2e_pid"
wait_success "$web_build_pid" 'web build' "$log_dir/web-build.log"
wait_success "$web_e2e_pid" 'E2E parallel with web build' "$log_dir/e2e-parallel-web.log"
background_pids=""

current_step='two concurrent api:check processes'
mise run api:check >"$log_dir/api-one.log" 2>&1 &
api_one_pid=$!
background_pids="$background_pids $api_one_pid"
mise run api:check >"$log_dir/api-two.log" 2>&1 &
api_two_pid=$!
background_pids="$background_pids $api_two_pid"
wait_success "$api_one_pid" 'first concurrent api:check' "$log_dir/api-one.log"
wait_success "$api_two_pid" 'second concurrent api:check' "$log_dir/api-two.log"
background_pids=""

current_step='failed E2E process isolation'
pnpm --dir frontend/apps/web test:e2e -- tests/e2e/m31-16-file-that-must-not-exist.spec.ts \
  >"$log_dir/expected-failure.log" 2>&1 &
failure_pid=$!
background_pids="$background_pids $failure_pid"
mise run api:check >"$log_dir/api-beside-failure.log" 2>&1 &
success_pid=$!
background_pids="$background_pids $success_pid"
if wait "$failure_pid"; then
  echo 'The E2E failure isolation probe unexpectedly succeeded.' >&2
  exit 1
fi
wait_success "$success_pid" 'api:check beside a failed process' "$log_dir/api-beside-failure.log"
background_pids=""

current_step='caller-owned runtime preservation'
protected_runtime="$test_dir/caller-owned-runtime"
mkdir -p "$protected_runtime"
printf 'preserve\n' >"$protected_runtime/sentinel"
if HORSE_E2E_RUNTIME_DIR="$protected_runtime" MISE_BIN=false \
  node scripts/run-playwright.mjs >"$log_dir/protected-runtime.log" 2>&1; then
  echo 'Playwright accepted a caller-owned runtime directory.' >&2
  exit 1
fi
test -f "$protected_runtime/sentinel"

current_step='automatic E2E timeout cleanup'
timeout_runtime="$test_dir/timeout-e2e"
HORSE_E2E_RUNTIME_DIR="$timeout_runtime" HORSE_E2E_TEST_TIMEOUT_MS=1500 \
  node scripts/run-playwright.mjs tests/e2e/m31-r14-checkpoint.spec.ts \
  >"$log_dir/timeout-e2e.log" 2>&1 &
timeout_pid=$!
background_pids="$background_pids $timeout_pid"
wait_for_file "$timeout_runtime/resources.json" "$timeout_pid"
timeout_backend_port="$(node -p "JSON.parse(require('fs').readFileSync('$timeout_runtime/resources.json')).backendPort")"
timeout_web_port="$(node -p "JSON.parse(require('fs').readFileSync('$timeout_runtime/resources.json')).webPort")"
timeout_database="$(node -p "JSON.parse(require('fs').readFileSync('$timeout_runtime/resources.json')).databaseName")"
if wait "$timeout_pid"; then
  echo 'Timed E2E unexpectedly exited successfully.' >&2
  exit 1
else
  timeout_status=$?
  if [[ "$timeout_status" -ne 124 ]]; then
    echo "Timed E2E exited with $timeout_status instead of 124." >&2
    cat "$log_dir/timeout-e2e.log" >&2
    exit 1
  fi
fi
background_pids=""
test ! -e "$timeout_runtime"
test -z "$(listener_pids "$timeout_backend_port")"
test -z "$(listener_pids "$timeout_web_port")"
test -z "$(find_database "$timeout_database")"

current_step='interrupted E2E cleanup'
interrupted_runtime="$test_dir/interrupted-e2e"
HORSE_E2E_RUNTIME_DIR="$interrupted_runtime" \
  node scripts/run-playwright.mjs tests/e2e/m31-r14-checkpoint.spec.ts \
  >"$log_dir/interrupted-e2e.log" 2>&1 &
interrupted_pid=$!
background_pids="$background_pids $interrupted_pid"
wait_for_file "$interrupted_runtime/resources.json" "$interrupted_pid"
interrupted_backend_port="$(node -p "JSON.parse(require('fs').readFileSync('$interrupted_runtime/resources.json')).backendPort")"
interrupted_web_port="$(node -p "JSON.parse(require('fs').readFileSync('$interrupted_runtime/resources.json')).webPort")"
interrupted_database="$(node -p "JSON.parse(require('fs').readFileSync('$interrupted_runtime/resources.json')).databaseName")"
kill -TERM "$interrupted_pid"
if wait "$interrupted_pid"; then
  echo 'Interrupted E2E unexpectedly exited successfully.' >&2
  exit 1
else
  interrupted_status=$?
  if [[ "$interrupted_status" -ne 143 ]]; then
    echo "Interrupted E2E exited with $interrupted_status instead of 143." >&2
    cat "$log_dir/interrupted-e2e.log" >&2
    exit 1
  fi
fi
background_pids=""
test ! -e "$interrupted_runtime"
test -z "$(listener_pids "$interrupted_backend_port")"
test -z "$(listener_pids "$interrupted_web_port")"
test -z "$(find_database "$interrupted_database")"

current_step='pre-existing port owner preservation'
for pid in $existing_8080_pids; do
  if ! kill -0 "$pid"; then
    echo "Pre-existing port 8080 owner $pid was terminated." >&2
    exit 1
  fi
done

current_step='temporary database cleanup'
databases_after="$(list_harness_databases)"
if [[ "$databases_after" != "$databases_before" ]]; then
  echo "Temporary database set changed." >&2
  printf 'Before:\n%s\nAfter:\n%s\n' "$databases_before" "$databases_after" >&2
  exit 1
fi

current_step='temporary file cleanup'
runtime_remainder="$(find "$runtime_dir" -mindepth 1 -maxdepth 1 \
  \( -name 'horse-e2e.*' -o -name 'horse-openapi.*' -o -name 'horse-gradle.*' -o -name 'interrupted-e2e' \) \
  -print)"
if [[ -n "$runtime_remainder" ]]; then
  echo "Owned process runtime entries remain:" >&2
  printf '%s\n' "$runtime_remainder" >&2
  exit 1
fi

current_step='Git working tree preservation'
status_after="$(git status --porcelain=v1 --untracked-files=all)"
if [[ "$status_after" != "$status_before" ]]; then
  echo "Git working tree changed during isolation verification." >&2
  diff -u <(printf '%s\n' "$status_before") <(printf '%s\n' "$status_after") >&2 || :
  exit 1
fi

echo 'M31-16 harness isolation scenarios passed.'
