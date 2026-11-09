#!/usr/bin/env bash
# Falla si una aplicación cambia código de producción y la rama no toca ninguna de sus pruebas.
# Uso: exige-pruebas.sh <base> <head>
set -euo pipefail

changed=$(git diff --name-only --diff-filter=ACMR "$1...$2")
status=0

require() {
  local app=$1 production=$2 tests=$3 code
  code=$(grep -E "$production" <<<"$changed" | grep -Ev "$tests" || true)
  if [[ -n "$code" ]] && ! grep -Eq "$tests" <<<"$changed"; then
    echo "::error::$app cambia código sin tocar sus pruebas:"
    echo "$code"
    status=1
  fi
}

require api '^apps/api/src/main/java/' '^apps/api/src/test/'
require agents '^apps/agents/src/main/java/' '^apps/agents/src/test/'
require web '^apps/web/src/.*\.tsx?$' '^apps/web/src/.*\.test\.tsx?$'

exit "$status"
