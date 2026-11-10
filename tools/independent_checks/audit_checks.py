#!/usr/bin/env python3
"""audit_checks.py — D19 / T24 external auditor (spec §9.23). Python, stdlib only, no app code.

Recomputes from an exported SQLite DB:
  68,567 active rolls · 175.2% wh-625 utilisation (BigDecimal-grade Decimal) · 3,005 records (RES-004 rule)
  1,296 mappings · 124 movements. Cross-checks seed-report.txt. Prints PASS/FAIL lines; exit 0 iff all pass.
Usage: audit_checks.py <db> [seed-report.txt]
"""
import sqlite3
import sys
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

DB = Path(sys.argv[1])
REPORT = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(__file__).resolve().parent.parent.parent / "seed-report.txt"
report = {}
if REPORT.exists():
    for line in REPORT.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.startswith("alazhartex"):
            k, v = line.split("=", 1)
            report[k] = v

conn = sqlite3.connect(DB)
try:
    active = conn.execute("SELECT COALESCE(SUM(count),0) FROM rolls WHERE state='ACTIVE'").fetchone()[0]
    wh625 = conn.execute("""SELECT COALESCE(SUM(r.count),0) FROM rolls r
                            JOIN mappings m ON m.id = r.mapping_id
                            WHERE r.state='ACTIVE' AND m.warehouse_id='wh-625'""").fetchone()[0]
    cap625 = conn.execute("SELECT capacity_rolls FROM warehouses WHERE id='wh-625'").fetchone()[0]
    util = (Decimal(wh625) / Decimal(cap625) * Decimal(100)).quantize(Decimal("0.1"), rounding=ROUND_HALF_UP)
    records = sum(
        conn.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0]
        for t in ("warehouses", "fabrics", "colours", "mappings", "inventory", "movements", "shed_assignments")
    )
    mappings = conn.execute("SELECT COUNT(*) FROM mappings").fetchone()[0]
    movements = conn.execute("SELECT COUNT(*) FROM movements").fetchone()[0]
finally:
    conn.close()

results = [
    ("active_rolls", active, 68567),
    ("wh625_utilisation_pct", str(util), "175.2"),
    ("total_records", records, 3005),
    ("fabric_colour_mappings", mappings, 1296),
    ("movements", movements, 124),
]
ok = True
for name, actual, expected in results:
    good = str(actual) == str(expected)
    if name in report:
        good = good and report[name] == str(expected)
    ok = ok and good
    print(f"{'PASS' if good else 'FAIL'} {name} actual={actual} expected={expected} (report={report.get(name, 'n/a')})")
print(f"verdict={'PASS' if ok else 'FAIL'}")
sys.exit(0 if ok else 1)
