#!/usr/bin/env bash
set -euo pipefail

required_files=(
  "AGENTS.md"
  "mise.toml"
  "compose.yaml"
  "backend/AGENTS.md"
  "backend/gradlew"
  "backend/build.gradle"
  "docs/tasks/TEMPLATE.md"
  "docs/tasks/active/README.md"
  "frontend/pnpm-workspace.yaml"
  "frontend/apps/web/package.json"
  "frontend/apps/mobile/package.json"
  "scripts/loop-ready-check.sh"
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
command -v git >/dev/null
command -v actionlint >/dev/null

java -version 2>&1 | head -n 1
node --version
pnpm --version
docker --version
actionlint --version
