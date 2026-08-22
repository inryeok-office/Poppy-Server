#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

fail=0

require_file() {
  if [ ! -f "$1" ]; then
    echo "MISSING: $1"
    fail=1
  fi
}

require_lf() {
  if grep -qU $'\r' "$1" 2>/dev/null; then
    echo "NOT LF-ONLY: $1"
    fail=1
  fi
}

require_file "CLAUDE.md"
require_file "AGENTS.md"

if [ -f "CLAUDE.md" ] && [ -f "AGENTS.md" ]; then
  if ! cmp -s "CLAUDE.md" "AGENTS.md"; then
    echo "MISMATCH: CLAUDE.md and AGENTS.md are not byte-identical"
    fail=1
  else
    echo "OK: CLAUDE.md and AGENTS.md are byte-identical"
  fi
  require_lf "CLAUDE.md"
  require_lf "AGENTS.md"
fi

required_docs=(
  "README.md"
  "CONTRIBUTING.md"
  "SECURITY.md"
  "docs/git-workflow.md"
  "docs/commit-convention.md"
  "docs/ai-workflow.md"
  "docs/architecture.md"
  "docs/configuration.md"
  "docs/api-convention.md"
  "docs/testing.md"
  "docs/ci.md"
  "docs/pull-request-convention.md"
)

for doc in "${required_docs[@]}"; do
  require_file "$doc"
done

if [ "$fail" -ne 0 ]; then
  echo "harness-check FAILED"
  exit 1
fi

echo "harness-check PASSED"
