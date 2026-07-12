#!/usr/bin/env bash
set -euo pipefail

mise_bin="${MISE_BIN:-mise}"

if [[ -n "${VERIFY_CHANGED_FILES:-}" ]]; then
  changed_files="$VERIFY_CHANGED_FILES"
elif ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  exec "$mise_bin" run verify:all
elif [[ -n "${VERIFY_CHANGED_BASE:-}" ]]; then
  changed_files="$(git diff --name-only "$VERIFY_CHANGED_BASE"...HEAD)"
else
  changed_files="$({
    git diff --name-only HEAD
    git ls-files --others --exclude-standard
  } | sort -u)"
fi

if [[ -z "$changed_files" ]]; then
  echo "no changed files"
  exit 0
fi

if grep -qE '^(mise\.toml|compose\.yaml|\.editorconfig|scripts/|frontend/package\.json|frontend/pnpm-lock\.yaml|frontend/pnpm-workspace\.yaml)' <<<"$changed_files"; then
  exec "$mise_bin" run verify:all
fi

run_backend=false
run_api=false
run_web=false
run_mobile=false
run_docs=false

grep -qE '^backend/' <<<"$changed_files" && run_backend=true
grep -qE '^(backend/openapi/|backend/src/main/|frontend/openapitools\.json|frontend/packages/api-client/|scripts/(generate|check)-api-client\.sh)' <<<"$changed_files" && run_api=true
grep -qE '^frontend/apps/web/' <<<"$changed_files" && run_web=true
grep -qE '^frontend/apps/mobile/' <<<"$changed_files" && run_mobile=true
if grep -qE '^frontend/packages/(shared-types|shared-utils)/' <<<"$changed_files"; then
  run_web=true
  run_mobile=true
fi
grep -qE '^(docs/|TMP\.md$|AGENTS\.md$|backend/AGENTS\.md$)' <<<"$changed_files" && run_docs=true

if [[ "$run_backend" == true ]]; then
  "$mise_bin" run backend:check
  "$mise_bin" run backend:integration
fi
if [[ "$run_api" == true ]]; then
  "$mise_bin" run api:check
fi
if [[ "$run_web" == true ]]; then
  "$mise_bin" run web:lint
  "$mise_bin" run web:typecheck
  "$mise_bin" run web:test
  "$mise_bin" run web:build
fi
if [[ "$run_mobile" == true ]]; then
  "$mise_bin" run mobile:lint
  "$mise_bin" run mobile:typecheck
  "$mise_bin" run mobile:test
  "$mise_bin" run mobile:export
fi
if [[ "$run_docs" == true ]]; then
  "$mise_bin" run docs:check
fi
