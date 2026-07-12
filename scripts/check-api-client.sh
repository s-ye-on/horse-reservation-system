#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT

"$root_dir/scripts/generate-api-client.sh" \
  "$temporary_dir/openapi" \
  "$temporary_dir/api-client"

diff -ru "$root_dir/backend/openapi" "$temporary_dir/openapi"
diff -ru "$root_dir/frontend/packages/api-client/src" "$temporary_dir/api-client"
