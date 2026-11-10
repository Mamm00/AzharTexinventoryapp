#!/usr/bin/env bash
# G7 forbidden-token scan (AGENTS.md "Forbidden tokens"). Exits 1 and prints file:line on any hit.
# Comment lines (//, *, /*) are stripped before matching — bans apply to code, not KDoc.
# The seed generator is a CLI: println/printStackTrace are its output channel and are allowed there.
set -euo pipefail
cd "$(dirname "$0")/.."

APP_SRC=(app/src/main data/src/main domain/src/main)
APP_GEN=(generator/src/main)
mapfile -d '' CODE < <(find "${APP_SRC[@]}" generator/src/main -name '*.kt' -print0 2>/dev/null)
mapfile -d '' CODE_NOCLI < <(find "${APP_SRC[@]}" -name '*.kt' -print0 2>/dev/null)

strip_comments() { grep -vE '^[[:space:]]*(//|\*|/\*)' "$@"; }

fail=0
scan_in() {
    local label="$1" pattern="$2"; shift 2
    local hits
    hits="$(for f in "$@"; do strip_comments "$f" | grep -nE "$pattern" | sed "s|^|$f:|" || true; done)"
    if [ -n "$hits" ]; then
        echo "FORBIDDEN[$label]:" >&2
        echo "$hits" >&2
        fail=1
    fi
}

scan_in "todo"            '\b(TODO|FIXME|XXX)\b'                    "${CODE[@]}"
scan_in "println"         '\bprintln\s*\(|\bprintStackTrace\s*\('   "${CODE_NOCLI[@]}"
scan_in "runBlocking"     '\brunBlocking\b'                         "${CODE[@]}"
scan_in "globalscope"     'GlobalScope\.'                           "${CODE[@]}"
scan_in "allow-main"      'AllowMainThreadQueries'                  "${CODE[@]}"
scan_in "destructive-mig" 'fallbackToDestructiveMigration'          "${CODE[@]}"
scan_in "collectAsState"  '\.collectAsState\(\)'                    "${CODE[@]}"
scan_in "state-value-set" '_state\.value\s*='                       "${CODE[@]}"
scan_in "mutable-state-list" 'mutableStateListOf'                   "${CODE[@]}"
scan_in "barcodes"        '[Bb]arcode|serial_number|serialNumber'   "${CODE[@]}"

while IFS= read -r file; do
    if grep -qE 'LazyColumn\(|LazyVerticalGrid\(' "$file" && ! grep -qE 'key\s*=' "$file"; then
        echo "FORBIDDEN[lazy-key]: $file uses lazy list without key" >&2
        fail=1
    fi
done < <(printf '%s\n' "${CODE[@]}")

for file in "${CODE[@]}"; do
    if strip_comments "$file" | grep -Pzo 'catch\s*\([^)]*\)\s*\{\s*\}' >/dev/null 2>&1; then
        echo "FORBIDDEN[empty-catch]: $file" >&2
        fail=1
    fi
done

if [ "$fail" -ne 0 ]; then
    echo "check_forbidden_tokens: FAILED" >&2
    exit 1
fi
echo "check_forbidden_tokens: OK (${#CODE[@]} files)"
