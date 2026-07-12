#!/usr/bin/env bash
set -euo pipefail

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "loop requires an initialized Git repository" >&2
  exit 1
fi

active_tasks=()
for file in docs/tasks/active/*.md; do
  [[ -e "$file" || "$file" == "docs/tasks/active/*.md" ]] || continue
  [[ "$(basename "$file")" == "README.md" ]] && continue
  active_tasks+=("$file")
done

if [[ "${#active_tasks[@]}" -ne 1 ]]; then
  echo "loop requires exactly one active task; found ${#active_tasks[@]}" >&2
  exit 1
fi

active_task="${active_tasks[0]}"
status="$(awk '
  /^## 상태$/ { in_status = 1; next }
  in_status && NF { gsub(/`/, ""); print; exit }
' "$active_task")"

if [[ "$status" != "READY" && "$status" != "IN_PROGRESS" ]]; then
  echo "active task must be READY or IN_PROGRESS: $active_task ($status)" >&2
  exit 1
fi

task_id="$(basename "$active_task" .md | tr '[:upper:]' '[:lower:]')"
verification_task="verify:$task_id"

if ! mise tasks --all | awk '{print $1}' | grep -Fxq "$verification_task"; then
  echo "missing task-specific verification command: mise run $verification_task" >&2
  exit 1
fi

git ls-files --error-unmatch AGENTS.md backend/AGENTS.md >/dev/null
if git check-ignore -q "$active_task"; then
  echo "active task is ignored by Git: $active_task" >&2
  exit 1
fi

echo "loop ready: $active_task ($status), mise run $verification_task"
