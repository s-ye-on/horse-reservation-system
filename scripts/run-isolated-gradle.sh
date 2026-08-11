#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/horse-gradle.XXXXXX")"
export TMPDIR="$runtime_dir"

cleanup() {
  rm -rf "$runtime_dir"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

"$root_dir/backend/gradlew" -p "$root_dir/backend" --no-daemon \
  --project-cache-dir "$runtime_dir/project-cache" \
  -PisolatedBuildDir="$runtime_dir/build" \
  "$@"
