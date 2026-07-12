#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT

fake_mise="$temporary_dir/mise"
cat >"$fake_mise" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"$VERIFY_CHANGED_LOG"
EOF
chmod +x "$fake_mise"

assert_routes() {
  local name="$1"
  local files="$2"
  local expected="$3"
  local log="$temporary_dir/$name.log"

  VERIFY_CHANGED_FILES="$files" \
  VERIFY_CHANGED_LOG="$log" \
  MISE_BIN="$fake_mise" \
    "$root_dir/scripts/verify-changed.sh"

  actual="$(cat "$log" 2>/dev/null || true)"
  if [[ "$actual" != "$expected" ]]; then
    printf 'routing case failed: %s\nexpected:\n%s\nactual:\n%s\n' "$name" "$expected" "$actual" >&2
    exit 1
  fi
}

assert_routes backend 'backend/src/main/java/com/horse/Foo.java' $'run backend:check\nrun backend:integration\nrun api:check'
assert_routes web 'frontend/apps/web/src/App.tsx' $'run web:lint\nrun web:typecheck\nrun web:test\nrun web:build'
assert_routes shared 'frontend/packages/shared-types/src/index.ts' $'run web:lint\nrun web:typecheck\nrun web:test\nrun web:build\nrun mobile:lint\nrun mobile:typecheck\nrun mobile:test\nrun mobile:export'
assert_routes docs 'docs/product/requirements.md' 'run docs:check'
assert_routes global 'mise.toml' 'run verify:all'

base_repo="$temporary_dir/base-repo"
base_log="$temporary_dir/base.log"
mkdir -p "$base_repo/frontend/apps/web/src"
git -C "$base_repo" init -q
git -C "$base_repo" config user.name test
git -C "$base_repo" config user.email test@example.com
printf 'initial\n' >"$base_repo/README.md"
git -C "$base_repo" add README.md
git -C "$base_repo" commit -qm initial
base_ref="$(git -C "$base_repo" rev-parse HEAD)"
printf 'export {};\n' >"$base_repo/frontend/apps/web/src/new.ts"
git -C "$base_repo" add frontend/apps/web/src/new.ts
git -C "$base_repo" commit -qm web-change
(
  cd "$base_repo"
  VERIFY_CHANGED_BASE="$base_ref" \
  VERIFY_CHANGED_LOG="$base_log" \
  MISE_BIN="$fake_mise" \
    "$root_dir/scripts/verify-changed.sh"
)
expected_base=$'run web:lint\nrun web:typecheck\nrun web:test\nrun web:build'
actual_base="$(cat "$base_log")"
if [[ "$actual_base" != "$expected_base" ]]; then
  printf 'base-ref routing failed\nexpected:\n%s\nactual:\n%s\n' "$expected_base" "$actual_base" >&2
  exit 1
fi

echo "verify-changed routing tests passed"
