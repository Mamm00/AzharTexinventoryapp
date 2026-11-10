#!/usr/bin/env python3
"""spec_oracle.py — O2 (spec §9.1). Independent Python recomputation of every §4.2 claim.

Reads seed-alazhartex.json + seed-report.txt (path args). Writes spec_report.txt.
All expected values below come from the MASTER SPEC (Sections 4.2, 9), NOT from the
generator. Zero shared code with the app or the generator. ≤ 200 lines by design (RES-006).
Exit 0 iff every check passes.
"""
import hashlib
import json
import sys
from collections import Counter
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
JSON_PATH = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "seed-alazhartex.json"
REPORT_PATH = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "seed-report.txt"
SPEC_REPORT = ROOT / "spec_report.txt"

UNSETTLED = object()
results = []

def check(name, actual, expected):
    ok = actual == expected
    results.append((name, actual, expected, ok))
    return ok

def fail(name, msg):
    results.append((name, msg, "", False))

data = json.loads(JSON_PATH.read_text(encoding="utf-8"))
raw_bytes = JSON_PATH.read_bytes()
report_lines = dict(
    line.split("=", 1) for line in REPORT_PATH.read_text(encoding="utf-8").splitlines()
    if "=" in line and not line.startswith("alazhartex")
)

warehouses, fabrics, colours = data["warehouses"], data["fabrics"], data["colours"]
mappings, inventory, movements = data["mappings"], data["inventory"], data["movements"]
rolls, assignments, accounts = data["rolls"], data["shed_assignments"], data["accounts"]
settings = data["settings"]

# --- identity of the seed itself ---
check("json_sha256_sidecar_match", hashlib.sha256(raw_bytes).hexdigest(), report_lines.get("seed_json_sha256"))
check("audit_log_seeded_empty", len(data["audit_log"]), 0)
check("sessions_seeded_empty", len(data["sessions"]), 0)

# --- §4.2 headline counts (RES-004 record rule) ---
records = len(warehouses) + len(fabrics) + len(colours) + len(mappings) + len(inventory) + len(movements) + len(assignments)
check("total_records", records, 3005)
check("sheds", len(warehouses), 10)
check("fabrics", len(fabrics), 61)
check("colours", len(colours), 208)
grades = Counter(c["grade"] for c in colours)
check("grade_A", grades["A"], 65)
check("grade_B", grades["B"], 70)
check("grade_C", grades["C"], 54)
check("grade_D", grades["D"], 19)
check("colour_families", len({c["family"] for c in colours}), 11)
check("fabric_families_cover", len({f["family"] for f in fabrics}), 11)
check("fabric_colour_mappings", len(mappings), 1296)
check("inventory_rows", len(inventory), 1296)
check("movements", len(movements), 124)
mtype = Counter(m["type"] for m in movements)
check("movements_in", mtype["IN"], 40)
check("movements_out", mtype["OUT"], 40)
check("movements_transfer", mtype["TRANSFER_OUT"] + mtype["TRANSFER_IN"], 24)
check("movements_adjustment", mtype["ADJUSTMENT"], 20)
tpair = Counter(m["transfer_pair_id"] for m in movements if m["transfer_pair_id"])
check("movements_transfer_pairs", len(tpair), 12)
check("transfer_pair_structure", all(v == 2 for v in tpair.values()), True)

# --- rolls & utilisation ---
roll_rows = rolls
active = sum(r["count"] for r in roll_rows if r["state"] == "ACTIVE")
inactive = sum(r["count"] for r in roll_rows if r["state"] == "INACTIVE")
shed_of = {m["id"]: m["warehouse_id"] for m in mappings}
keeper_of = {w["id"]: w["keeper_account_id"] for w in warehouses}
shed_rolls = Counter()
for r in roll_rows:
    if r["state"] == "ACTIVE":
        shed_rolls[shed_of[r["mapping_id"]]] += r["count"]
keeper1 = sum(v for s, v in shed_rolls.items() if keeper_of[s] == "keeper1")
keeper2 = sum(v for s, v in shed_rolls.items() if keeper_of[s] == "keeper2")
check("active_rolls", active, 68567)
check("keeper1_active_rolls", keeper1, 51333)
check("keeper2_active_rolls", keeper2, 17234)
check("raw_rolls", active + inactive, 69787)
check("inactive_rolls", inactive, 1220)
check("inactive_mapping_rows", sum(1 for m in mappings if not m["is_active"]), 26)
wh625 = [m for m in mappings if m["warehouse_id"] == "wh-625"]
wh625_active = sum(shed_rolls[s] for s in ["wh-625"] if s in shed_rolls)
cap625 = next(w["capacity_rolls"] for w in warehouses if w["id"] == "wh-625")
util = (Decimal(wh625_active) / Decimal(cap625) * Decimal(100)).quantize(Decimal("0.1"), rounding=ROUND_HALF_UP)
check("wh625_active_rolls", wh625_active, 5255)
check("wh625_capacity_rolls", cap625, 3000)
check("wh625_utilisation_pct", str(util), "175.2")
check("wh625_mapped_colours", len(wh625), 180)
check("wh625_active_mappings", sum(1 for m in wh625 if m["is_active"]), 176)
check("keeper_colour_codes", sum(1 for c in colours if c["keeper_code"]), 7)

# --- zero-drift at seed: opening + Σdeltas == closing == opening_cache ---
delta = Counter()
for m in movements:
    delta[m["mapping_id"]] += m["qty_delta"]
mapping_ids = {m["id"] for m in mappings}
inv_by_mapping = {r["mapping_id"]: r for r in inventory}
drift = 0
neg = 0
cache_mismatch = 0
for mid in mapping_ids:
    row = inv_by_mapping.get(mid)
    if row is None:
        drift += 1
        continue
    opening_derived = row["closing"] - delta.get(mid, 0)
    if opening_derived < 0 or opening_derived != row["opening_cache"]:
        cache_mismatch += 1
check("drift_rows", drift, 0)
check("negative_or_mismatched_openings", cache_mismatch + neg, 0)
check("transfer_net_zero", sum(m["qty_delta"] for m in movements if m["type"].startswith("TRANSFER")), 0)
check("qty_delta_nonzero", all(m["qty_delta"] != 0 for m in movements), True)
check("unique_idempotency_keys", len({m["idempotency_key"] for m in movements}), len(movements))
check("unique_mapping_triple", len({(m["warehouse_id"], m["fabric_id"], m["colour_id"]) for m in mappings}), len(mappings))
check("grades_valid", set(grades) <= {"A", "B", "C", "D"}, True)
check("shed_assignments", len(assignments), 10)
check("accounts", len(accounts), 3)
check("settings_seeded", len(settings) >= 8, True)

# --- report artifact agrees with the same constants ---
for key, expected in [
    ("total_records", "3005"), ("sheds", "10"), ("fabrics", "61"), ("colours", "208"),
    ("fabric_colour_mappings", "1296"), ("movements", "124"), ("active_rolls", "68567"),
    ("raw_rolls", "69787"), ("inactive_rolls", "1220"), ("wh625_utilisation_pct", "175.2"),
]:
    check(f"report:{key}", report_lines.get(key), expected)

passed = sum(1 for *_, ok in results if ok)
total = len(results)
with SPEC_REPORT.open("w", encoding="utf-8", newline="\n") as fh:
    fh.write("alazhartex-spec-oracle v1\n")
    fh.write(f"checks={total}\n")
    for name, actual, expected, ok in results:
        fh.write(f"{'PASS' if ok else 'FAIL'} {name} actual={actual} expected={expected}\n")
    fh.write(f"verdict={'PASS' if passed == total else 'FAIL'}\n")
print(f"spec_oracle: {passed}/{total} checks passed -> {SPEC_REPORT.relative_to(ROOT)}")
sys.exit(0 if passed == total else 1)
