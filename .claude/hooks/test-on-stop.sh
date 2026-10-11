#!/usr/bin/env bash
# Stop hook: if code changed, run the full build so Claude can't finish a turn on a red build.
set -uo pipefail
cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}"

# Don't loop: if Claude is already continuing because of this hook, let it stop.
grep -Eq '"stop_hook_active"[[:space:]]*:[[:space:]]*true' && exit 0

# Skip when nothing under src/ or pom.xml changed.
git status --porcelain -- src pom.xml | grep -q . || exit 0

if ! docker info >/dev/null 2>&1; then
  echo "Docker is not running; start Docker Desktop so the Testcontainers tests can run." >&2
  exit 2
fi

if ! out=$(./mvnw -q -ntp verify 2>&1); then
  echo "./mvnw verify failed. Fix it before finishing:" >&2
  echo "$out" | grep -v '^WARNING' | tail -40 >&2
  exit 2
fi
