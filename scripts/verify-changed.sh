#!/usr/bin/env bash
set -euo pipefail

if [[ ! -d .git ]]; then
  exec mise run verify:all
fi

changed_files="$(git diff --name-only HEAD 2>/dev/null || true)"

if [[ -z "$changed_files" ]]; then
  echo "no changed files"
  exit 0
fi

if grep -qE '^(backend/|mise.toml|compose.yaml)' <<<"$changed_files"; then
  mise run backend:check
  mise run backend:integration
fi

if grep -qE '^(frontend/apps/web/|frontend/packages/|frontend/pnpm)' <<<"$changed_files"; then
  mise run web:lint
  mise run web:typecheck
  mise run web:test
  mise run web:build
fi

if grep -qE '^frontend/apps/mobile/' <<<"$changed_files"; then
  mise run mobile:lint
  mise run mobile:typecheck
  mise run mobile:test
  mise run mobile:export
fi

if grep -qE '^(docs/|TMP.md)' <<<"$changed_files"; then
  mise run docs:check
fi

