#!/usr/bin/env bash
# CI triage helper: runs gradle, and on failure re-emits the Kotlin compiler errors
# ("e: <path>:<line>:<col>: <msg>") as ::error annotations plus a log tail, so failures
# are visible via the check-runs annotations API even when blob logs/artifacts are unreachable.
set -uo pipefail
LOG=$(mktemp)
trap 'rm -f "$LOG"' EXIT

gradle --no-daemon "$@" 2>&1 | tee "$LOG"
rc=${PIPESTATUS[0]}
[ $rc -eq 0 ] && exit 0

esc() { printf '%s' "$1" | sed 's/%/%25/g; s/\r/%0D/g'; }

# Kotlin compiler diagnostics: "e: /abs/path/File.kt:12:34: message"
grep -Eo '^e: [^ ]+:[0-9]+:[0-9]+: .+$' "$LOG" | head -25 | while IFS= read -r line; do
  body=${line#e: }
  file=${body%%:*}; rest=${body#*:}
  lineno=${rest%%:*}; msg=${rest#*:}; msg=${msg#*:}
  rel=$(printf '%s' "$file" | sed 's#.*/AzharTexinventoryapp/##')
  printf '::error file=%s,line=%s::%s\n' "$(esc "$rel")" "$(esc "$lineno")" "$(esc "$msg")"
done

# Test-runner style failures: "ClassName > testName FAILED"
grep -E '> .*(FAILED|failed)$|AssertionError|Condition not met|ComparisonFailure' "$LOG" | head -15 | while IFS= read -r line; do
  printf '::error title=test::%s\n' "$(esc "$line")"
done

printf '::error title=gradle-tail::%s\n' "$(tail -n 80 "$LOG" | sed 's/%/%25/g; s/\r/%0D/g' | tr '\n' '%0A')"
exit $rc
