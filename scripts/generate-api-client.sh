#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
spec_dir="${1:-$root_dir/backend/openapi}"
client_dir="${2:-$root_dir/frontend/packages/api-client/src}"

mkdir -p "$spec_dir"
rm -rf "$client_dir"

docker compose --project-directory "$root_dir" up -d --wait mysql
"$root_dir/backend/gradlew" -p "$root_dir/backend" generateOpenApiDocs \
  -PopenapiOutputDir="$spec_dir"

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
  fi
done < <(find "$client_dir" -type f -name '*.ts')
