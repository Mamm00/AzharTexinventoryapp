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

# Kotlin compiler diagnostics. Formats seen:
#   Kotlin 1.9: "e: /abs/File.kt:12:34: message"
#   Kotlin 2.x: "e: file:///abs/File.kt:12:34 message"  (no colon after column)
grep -E '^e: [^ ]+:[0-9]+:[0-9]+(: | )' "$LOG" | head -25 | while IFS= read -r line; do
  parsed=$(printf '%s\n' "$line" | sed -E 's#^e: ([^ ]+):([0-9]+):([0-9]+)(: )? #\1|\2|#')
  file=${parsed%%|*}; rest=${parsed#*|}
  lineno=${rest%%|*}; msg=${rest#*|}
  rel=$(printf '%s' "$file" | sed 's#.*/AzharTexinventoryapp/##; s#file://##')
  printf '::error file=%s,line=%s::%s\n' "$(esc "$rel")" "$(esc "$lineno")" "$(esc "$msg")"
done

# Lint text reports: "/path/file.kt:12: Error: message [IssueType]"
find . -path '*/build/*' -name 'lint-results-*.txt' -print 2>/dev/null | while IFS= read -r report; do
  grep -E '^[^ ]+:[0-9]+: Error: ' "$report" | head -60 | while IFS= read -r line; do
    parsed=$(printf '%s\n' "$line" | sed -E 's#^([^ ]+):([0-9]+): Error: #\1|\2|#')
    file=${parsed%%|*}; rest=${parsed#*|}
    lineno=${rest%%|*}; msg=${rest#*|}
    rel=$(printf '%s' "$file" | sed 's#.*/AzharTexinventoryapp/##')
    printf '::error file=%s,line=%s::%s\n' "$(esc "$rel")" "$(esc "$lineno")" "$(esc "$msg")"
  done
done

# Test-runner style failures: "ClassName > testName FAILED"
grep -E '> .*(FAILED|failed)$|AssertionError|Condition not met|ComparisonFailure' "$LOG" | head -15 | while IFS= read -r line; do
  printf '::error title=test::%s\n' "$(esc "$line")"
done

printf '::error title=gradle-tail::%s\n' "$(tail -n 80 "$LOG" | sed 's/%/%25/g; s/\r/%0D/g' | tr '\n' '%0A')"
exit $rc
