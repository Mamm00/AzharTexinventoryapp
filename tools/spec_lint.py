#!/usr/bin/env python3
"""spec_lint.py — G10 contradiction scanner (spec §9.15).

Scans docs/MASTER_SPEC.md (faithful normative distillation of the master prompt)
for contradiction markers, and enforces that RESOLUTIONS.md exists, contains every
required resolution ID, and marks none of them OPEN.

Exit 0 = clean. Exit 1 = contradiction/unresolved resolution. No suppression flags.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SPEC = ROOT / "docs" / "MASTER_SPEC.md"
RESOLUTIONS = ROOT / "RESOLUTIONS.md"

# Rule table: each rule = (id, required_resolution_id, regex signalling the tension exists in the spec)
RULES = [
    ("L-approval-vs-merge",      "RES-001", r"push to main are NEVER yours|AI opens the PR"),
    ("L-derived-vs-invented",    "RES-002", r"Never print a number you have not derived|locale-formatted via NumberFormat"),
    ("L-admin-all10",            "RES-003", r"admin / admin123 \u2192 all 10|admin .* all 10"),
    ("L-records-3005",           "RES-004", r"Total records 3,005"),
    ("L-iso-vs-epoch",           "RES-005", r"fixed ISO-8601 UTC instants|epoch millis UTC"),
    ("L-oracle-200-lines",       "RES-006", r"\u2264 200 lines|<= 200 lines"),
    ("L-schema-diff-env",        "RES-007", r"Room schema diff|schema JSON must match expected"),
    ("L-session-refresh",        "RES-008", r"continuous use|NO silent refresh"),
    ("L-mcp-vs-fallback",        "RES-009", r"mobile verification runner|FALLBACK VERIFICATION CHAIN"),
    ("L-three-candidates",       "RES-010", r"THREE candidate solutions"),
    ("L-parallel-agents",        "RES-011", r"PARALLEL AGENTS"),
    ("L-drift-always-0",         "RES-012", r"drift indicator always 0|always 0"),
    ("L-digits-toggle",          "RES-013", r"Arabic-Indic|Western digits"),
    ("L-writecontext-dao",       "RES-014", r"WriteContext"),
    ("L-generator-offline",      "RES-015", r"Kotlin seed generator"),
    ("L-skills-cli",             "RES-016", r"android skills add"),
    ("L-snapshot-emulator",      "RES-017", r"exact emulator image IDs"),
    ("L-sales-manager-label",    "RES-018", r"SalesManager = read-only|SALES_MANAGER"),
    ("L-seed-report-truth",      "RES-019", r"seed-report\.txt"),
    ("L-ci-cost",                "RES-020", r"emulator matrix|connectedAndroidTest"),
]

def fail(msg: str) -> int:
    print(f"spec_lint: FAIL — {msg}")
    return 1

def main() -> int:
    if not SPEC.exists():
        return fail(f"missing {SPEC.relative_to(ROOT)}")
    if not RESOLUTIONS.exists():
        return fail("missing RESOLUTIONS.md")

    spec_text = SPEC.read_text(encoding="utf-8")
    res_text = RESOLUTIONS.read_text(encoding="utf-8")

    ok = True
    for rule_id, res_id, pattern in RULES:
        if not re.search(pattern, spec_text, flags=re.IGNORECASE):
            print(f"spec_lint: note — rule {rule_id}: tension pattern not found in spec distillation (kept as documentation)")
        entry = re.search(rf"- {res_id}\b.*?STATUS:\s*(RESOLVED|OPEN)", res_text, flags=re.DOTALL)
        if not entry:
            ok = False
            print(f"spec_lint: FAIL — {res_id} (required by {rule_id}) missing from RESOLUTIONS.md")
        elif entry.group(1) != "RESOLVED":
            ok = False
            print(f"spec_lint: FAIL — {res_id} is OPEN")

    for m in re.finditer(r"- (RES-\d+)\b[^\n]*?STATUS:\s*OPEN", res_text):
        ok = False
        print(f"spec_lint: FAIL — {m.group(1)} marked OPEN")

    if re.search(r"\bUNRESOLVED\b", res_text, flags=re.IGNORECASE):
        ok = False
        print("spec_lint: FAIL — literal UNRESOLVED marker present")

    if not ok:
        return 1
    print(f"spec_lint: PASS — {len(RULES)} tension rules bound to resolutions; no OPEN resolutions")
    return 0

if __name__ == "__main__":
    sys.exit(main())
