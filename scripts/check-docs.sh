#!/usr/bin/env bash
set -euo pipefail

if rg -n '취소 또는 반려|`cancelled`[^\n]*(취소와 반려|반려 상태)' docs; then
  echo "rejected and cancelled terminology is conflated" >&2
  exit 1
fi

if rg -n '<TASK-ID>|<작업 제목>|<task-id>' docs/tasks/active --glob '*.md' --glob '!README.md'; then
  echo "active task contains unfilled template placeholders" >&2
  exit 1
fi

while IFS= read -r link; do
  target="${link#*(}"
  target="${target%)}"
  target="${target%%#*}"
  [[ -z "$target" || "$target" == http://* || "$target" == https://* ]] && continue
  source_file="${link%%:*}"
  source_dir="$(dirname "$source_file")"
  if [[ ! -e "$source_dir/$target" ]]; then
    echo "broken markdown link: $source_file -> $target" >&2
    exit 1
  fi
done < <(rg -n -o '\[[^]]+\]\([^)]+\)' docs)

echo "documentation checks passed"
