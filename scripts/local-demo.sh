#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${TMPDIR:-/tmp}/horse-local-demo"
BACKEND_PORT=8081
PROXY_PORT=8080
WEB_PORT=5173
JAVA_BIN="$(command -v java 2>/dev/null || true)"
NODE_BIN="$(command -v node 2>/dev/null || true)"

mkdir -p "$RUNTIME_DIR"

start_demo() {
  stop_demo
  trap 'stop_demo' ERR
  ensure_port_available "$BACKEND_PORT" backend
  ensure_port_available "$PROXY_PORT" proxy
  ensure_port_available "$WEB_PORT" web

  mise run db:up
  mise run backend:build
  prepare_demo_secret
  prepare_demo_data

  start_process backend "$ROOT_DIR/scripts/local-demo.sh" run-backend "$JAVA_BIN"
  wait_for_url "http://127.0.0.1:$BACKEND_PORT/actuator/health" backend

  start_process proxy "$ROOT_DIR/scripts/local-demo.sh" run-proxy "$NODE_BIN"
  wait_for_url "http://127.0.0.1:$PROXY_PORT/actuator/health" proxy

  start_process web "$ROOT_DIR/scripts/local-demo.sh" run-web "$NODE_BIN"
  wait_for_url "http://127.0.0.1:$WEB_PORT/" web

  trap - ERR
  echo "Local demo is ready: http://127.0.0.1:$WEB_PORT"
}

stop_demo() {
  stop_process web
  stop_process proxy
  stop_process backend
}

show_status() {
  show_process web "http://127.0.0.1:$WEB_PORT/"
  show_process proxy "http://127.0.0.1:$PROXY_PORT/actuator/health"
  show_process backend "http://127.0.0.1:$BACKEND_PORT/actuator/health"
}

prepare_demo_secret() {
  if [[ ! -s "$RUNTIME_DIR/jwt-secret" ]]; then
    umask 077
    "$NODE_BIN" --input-type=module -e \
      "import { randomBytes } from 'node:crypto'; process.stdout.write(randomBytes(32).toString('hex'))" \
      > "$RUNTIME_DIR/jwt-secret"
  fi
}

prepare_demo_data() {
  docker exec -i horse-mysql sh -c \
    'exec env MYSQL_PWD="$MYSQL_PASSWORD" mysql --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --default-character-set=utf8mb4' <<'SQL'
INSERT INTO members (
  auth_subject, name, phone, general_ride_count,
  dressage_approved, jumping_approved, large_arena_allowed
) VALUES (
  'local-demo-member', '로컬 데모 회원', '010-0000-0000', 26, TRUE, TRUE, TRUE
)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  phone = VALUES(phone),
  general_ride_count = VALUES(general_ride_count),
  dressage_approved = VALUES(dressage_approved),
  jumping_approved = VALUES(jumping_approved),
  large_arena_allowed = VALUES(large_arena_allowed);

INSERT INTO coupons (
  member_id, coupon_type, total_count, remaining_count, held_count, status, created_by
)
SELECT id, 'general', 10, 10, 0, 'active', 'local-demo'
FROM members
WHERE auth_subject = 'local-demo-member'
  AND NOT EXISTS (
    SELECT 1 FROM coupons
    WHERE member_id = members.id AND created_by = 'local-demo' AND status = 'active'
  );

INSERT IGNORE INTO time_slot_capacities (
  lesson_date, start_time, total_capacity, round_arena_capacity, class_capacity_json, admin_closed
) VALUES
  (DATE_ADD(CURDATE(), INTERVAL 1 DAY), '10:00:00', 8, 4, '{"FIRST_RIDE":4,"ROUND_BEGINNER":4,"ROUND_TROT":4,"LARGE_ARENA_BEGINNER":5,"LARGE_ARENA_TROT":5,"DRESSAGE":5,"JUMPING":5}', FALSE),
  (DATE_ADD(CURDATE(), INTERVAL 2 DAY), '10:00:00', 8, 4, '{"FIRST_RIDE":4,"ROUND_BEGINNER":4,"ROUND_TROT":4,"LARGE_ARENA_BEGINNER":5,"LARGE_ARENA_TROT":5,"DRESSAGE":5,"JUMPING":5}', FALSE),
  (DATE_ADD(CURDATE(), INTERVAL 3 DAY), '10:00:00', 8, 4, '{"FIRST_RIDE":4,"ROUND_BEGINNER":4,"ROUND_TROT":4,"LARGE_ARENA_BEGINNER":5,"LARGE_ARENA_TROT":5,"DRESSAGE":5,"JUMPING":5}', FALSE);
SQL
}

start_process() {
  local name="$1"
  shift
  local label="com.horse.local-demo.$name"
  launchctl remove "$label" 2>/dev/null || true
  : > "$RUNTIME_DIR/$name.log"
  launchctl submit -l "$label" \
    -o "$RUNTIME_DIR/$name.log" \
    -e "$RUNTIME_DIR/$name.log" \
    -- "$@"
}

run_backend() {
  local java_bin="$1"
  export SERVER_PORT="$BACKEND_PORT"
  export JWT_SECRET="$(<"$RUNTIME_DIR/jwt-secret")"
  exec "$java_bin" -jar "$ROOT_DIR/backend/build/libs/horse-backend-0.0.1-SNAPSHOT.jar"
}

run_proxy() {
  local node_bin="$1"
  export DEMO_BACKEND_PORT="$BACKEND_PORT"
  export DEMO_PROXY_PORT="$PROXY_PORT"
  export JWT_SECRET="$(<"$RUNTIME_DIR/jwt-secret")"
  exec "$node_bin" "$ROOT_DIR/scripts/local-demo-proxy.mjs"
}

run_web() {
  local node_bin="$1"
  exec "$node_bin" "$ROOT_DIR/frontend/apps/web/node_modules/vite/bin/vite.js" \
    "$ROOT_DIR/frontend/apps/web" \
    --host 127.0.0.1 --port "$WEB_PORT"
}

stop_process() {
  local name="$1"
  launchctl remove "com.horse.local-demo.$name" 2>/dev/null || true
}

ensure_port_available() {
  local port="$1"
  local name="$2"
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Port $port is already in use. Run 'mise run demo:down' or stop the existing $name process." >&2
    exit 1
  fi
}

wait_for_url() {
  local url="$1"
  local name="$2"
  for _ in {1..60}; do
    if curl --fail --silent --output /dev/null "$url"; then
      return 0
    fi
    sleep 0.5
  done
  echo "$name did not become ready. See $RUNTIME_DIR/$name.log" >&2
  exit 1
}

show_process() {
  local name="$1"
  local url="$2"
  local label="com.horse.local-demo.$name"
  local service
  service="$(launchctl print "gui/$(id -u)/$label" 2>/dev/null || true)"
  if [[ -n "$service" ]] && curl --fail --silent --output /dev/null "$url"; then
    local pid
    pid="$(awk '/^[[:space:]]*pid = / { print $3; exit }' <<<"$service")"
    echo "$name: running (pid ${pid:-unknown})"
  else
    echo "$name: stopped"
  fi
}

case "${1:-}" in
  up) start_demo ;;
  down) stop_demo ;;
  status) show_status ;;
  run-backend) run_backend "$2" ;;
  run-proxy) run_proxy "$2" ;;
  run-web) run_web "$2" ;;
  *) echo "Usage: $0 {up|down|status}" >&2; exit 2 ;;
esac
