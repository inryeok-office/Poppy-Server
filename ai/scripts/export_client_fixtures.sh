#!/usr/bin/env bash
set -euo pipefail

CLIENT_DIR="${POPPY_CLIENT_DIR:-$HOME/work/Poppy-Client}"
CLIENT_REF="${POPPY_CLIENT_REF:-origin/main}"
NODE_BIN="${NODE_BIN:-node}"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/../tests/fixtures/client_parity.json"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

show() { git -C "$CLIENT_DIR" show "$CLIENT_REF:$1"; }

show src/features/simulation/model/motionPlan.ts > "$WORK/motionPlan.ts"
show src/features/ai/model/validate.ts | sed "s#from '@/features/simulation'#from './limits.ts'#" > "$WORK/validate.ts"
show src/features/ai/lib/sanitizeInput.ts > "$WORK/sanitizeInput.ts"
show src/features/ai/model/planner.ts | sed "s#from '@/features/simulation'#from './limits.ts'#" > "$WORK/planner.ts"
{
  show src/features/simulation/model/safety.ts | awk '/^export const LIMITS = \{/,/^\} as const;/'
} > "$WORK/limits.ts"
sed -i "s#from './types'#from './types.ts'#; s#from './motionPlan'#from './motionPlan.ts'#" "$WORK"/*.ts
echo "export {};" > "$WORK/types.ts"
cp "$HERE/client_fixture_cases.mjs" "$WORK/run.mjs"

COMMIT="$(git -C "$CLIENT_DIR" rev-parse --short "$CLIENT_REF")"
(cd "$WORK" && "$NODE_BIN" --experimental-strip-types --no-warnings run.mjs "$COMMIT") > "$OUT"
echo "wrote $OUT (client $COMMIT)"
