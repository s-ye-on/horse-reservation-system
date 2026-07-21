#!/usr/bin/env bash
set -euo pipefail

duplicates="$(
  docker compose exec -T mysql sh -c '
    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql \
      --batch --skip-column-names \
      --user=root "$MYSQL_DATABASE"
  ' <<'SQL'
SELECT
  member_id,
  lesson_date,
  start_time,
  GROUP_CONCAT(id ORDER BY id SEPARATOR ',') AS reservation_ids
FROM reservations
WHERE status IN ('pending_admin_approval', 'pending_payment', 'confirmed')
GROUP BY member_id, lesson_date, start_time
HAVING COUNT(*) > 1
ORDER BY member_id, lesson_date, start_time;
SQL
)"

if [[ -n "$duplicates" ]]; then
  echo "active reservation duplicates found (member_id, lesson_date, start_time, reservation_ids):" >&2
  echo "$duplicates" >&2
  exit 1
fi

echo "active reservation duplicate preflight passed"
