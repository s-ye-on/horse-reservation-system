#!/usr/bin/env bash
set -euo pipefail

required_files=(
  "mise.toml"
  "compose.yaml"
  "backend/gradlew"
  "backend/build.gradle"
  "frontend/pnpm-workspace.yaml"
  "frontend/apps/web/package.json"
  "frontend/apps/mobile/package.json"
)

for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "missing required file: $file" >&2
    exit 1
  fi
done

command -v java >/dev/null
command -v node >/dev/null
command -v pnpm >/dev/null
command -v docker >/dev/null

java -version 2>&1 | head -n 1
node --version
pnpm --version
docker --version

