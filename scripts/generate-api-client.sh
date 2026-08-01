#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
spec_dir="${1:-$root_dir/backend/openapi}"
client_dir="${2:-$root_dir/frontend/packages/api-client/src}"
runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/horse-openapi.XXXXXX")"
export TMPDIR="$runtime_dir"
database_name="horse_openapi_${$}_${RANDOM}"
database_created=false

allocate_port() {
  node --input-type=module -e '
    import net from "node:net"
    const server = net.createServer()
    server.listen(0, "127.0.0.1", () => {
      process.stdout.write(String(server.address().port))
      server.close()
    })
  '
}

drop_database() {
  if [[ "$database_created" != true ]]; then
    return
  fi

  printf 'DROP DATABASE IF EXISTS `%s`;\n' "$database_name" \
    | docker exec -i horse-mysql sh -c \
      'exec env MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root --batch --skip-column-names'
}

cleanup() {
  drop_database
  rm -rf "$runtime_dir"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

mkdir -p "$spec_dir"
rm -rf "$client_dir"

docker compose --project-directory "$root_dir" up -d --wait mysql
database_user="$(docker exec horse-mysql sh -c 'printf %s "$MYSQL_USER"')"
if [[ ! "$database_user" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo 'MySQL test user contains unsupported characters.' >&2
  exit 1
fi

database_created=true
printf 'CREATE DATABASE `%s` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\nGRANT ALL PRIVILEGES ON `%s`.* TO '\''%s'\''@'\''%%'\'';\n' \
  "$database_name" "$database_name" "$database_user" \
  | docker exec -i horse-mysql sh -c \
    'exec env MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root --batch --skip-column-names'

openapi_server_port="${OPENAPI_SERVER_PORT:-$(allocate_port)}"
DB_URL="jdbc:mysql://127.0.0.1:${MYSQL_PORT:-3306}/${database_name}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul" \
DB_USERNAME="${DB_USERNAME:-${MYSQL_USER:-horse}}" \
DB_PASSWORD="${DB_PASSWORD:-${MYSQL_PASSWORD:-horse_dev}}" \
  "$root_dir/backend/gradlew" -p "$root_dir/backend" --no-daemon \
    --project-cache-dir "$runtime_dir/gradle-project-cache" \
    -PisolatedBuildDir="$runtime_dir/backend-build" \
    -PopenapiServerPort="$openapi_server_port" \
    -PopenapiOutputDir="$spec_dir" \
    generateOpenApiDocs

pnpm --dir "$root_dir/frontend" exec openapi-generator-cli generate \
  -i "$spec_dir/horse-api.json" \
  -g typescript-fetch \
  -o "$client_dir" \
  --additional-properties=supportsES6=true,typescriptThreePlus=true \
  --global-property=apiDocs=false,modelDocs=false,apiTests=false,modelTests=false

rm -rf "$client_dir/.openapi-generator" "$client_dir/.openapi-generator-ignore"
find "$client_dir" -type f -name '*.ts' -exec perl -pi -e 's/[ \t]+$//' {} +
while IFS= read -r generated_file; do
  relative_file="${generated_file#"$client_dir/"}"
  tracked_file="frontend/packages/api-client/src/$relative_file"
  if ! git -C "$root_dir" cat-file -e "HEAD:$tracked_file" 2>/dev/null; then
    perl -0777 -pi -e 's/\s*\z/\n/' "$generated_file"
  elif git -C "$root_dir" cat-file blob "HEAD:$tracked_file" \
    | perl -0777 -ne 'exit(/\n\n\z/ ? 1 : 0)'; then
    perl -0777 -pi -e 's/\s*\z/\n/' "$generated_file"
  fi
done < <(find "$client_dir" -type f -name '*.ts')
