#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
preflight_sql="$project_root/backend/src/main/resources/db/preflight/m31-r02-preflight.sql"

issues="$(
	docker compose exec -T mysql sh -c '
		MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql \
			--batch --raw \
			--user=root "$MYSQL_DATABASE"
	' < "$preflight_sql"
)"

if [[ "$(printf '%s\n' "$issues" | wc -l | tr -d ' ')" -gt 1 ]]; then
	echo "M31-R02 schedule foundation preflight failed:" >&2
	echo "$issues" >&2
	echo "No data was changed. Resolve every reported row before applying Flyway V17." >&2
	exit 1
fi

echo "M31-R02 schedule foundation preflight passed"
