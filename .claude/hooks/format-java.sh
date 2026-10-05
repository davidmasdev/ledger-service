#!/usr/bin/env bash
# PostToolUse hook: format a .java file with Spotless right after Claude edits it.
set -euo pipefail
cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}"

pattern=$(node .claude/hooks/spotless-pattern.js)
[ -z "$pattern" ] && exit 0

if ! out=$(./mvnw -q -ntp spotless:apply "-DspotlessFiles=$pattern" 2>&1); then
  echo "Spotless could not format the file (likely a syntax error):" >&2
  echo "$out" | grep -v '^WARNING' | tail -20 >&2
  exit 2
fi
