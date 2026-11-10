#!/usr/bin/env python3
"""seed_to_sqlite.py — materialise seed-alazhartex.json into a local SQLite mirror.

Hand-written DDL mirroring the Room entities (snake_case, FK RESTRICT, uniques, CHECKs,
index on rolls(mapping_id, state), _manifest table per §9.8 R5). Independent of the app's
schema generator; used by the Python-side external auditors (D19) and integrity checks.
Usage: seed_to_sqlite.py <seed.json> <out.db>
"""
import json
import sqlite3
import sys
from datetime import datetime, timezone
from pathlib import Path

DDL = """
PRAGMA foreign_keys = ON;
CREATE TABLE accounts(
  id TEXT PRIMARY KEY NOT NULL,
  username TEXT NOT NULL UNIQUE,
  password_pbkdf2_sha256 TEXT NOT NULL,
  role TEXT NOT NULL CHECK(role IN ('ADMIN','KEEPER','SALES_MANAGER')),
  display_name TEXT NOT NULL
);
CREATE TABLE warehouses(
  id TEXT PRIMARY KEY NOT NULL,
  name_ar TEXT NOT NULL,
  name_en TEXT NOT NULL,
  capacity_rolls INTEGER NOT NULL CHECK(capacity_rolls >= 0),
  keeper_account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT
);
CREATE TABLE shed_assignments(
  account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
  warehouse_id TEXT NOT NULL REFERENCES warehouses(id) ON DELETE RESTRICT,
  PRIMARY KEY(account_id, warehouse_id)
);
CREATE TABLE fabrics(
  id TEXT PRIMARY KEY NOT NULL,
  name_ar TEXT NOT NULL,
  name_en TEXT NOT NULL,
  family TEXT NOT NULL
);
CREATE TABLE colours(
  id TEXT PRIMARY KEY NOT NULL,
  code TEXT NOT NULL UNIQUE,
  name_ar TEXT NOT NULL,
  name_en TEXT NOT NULL,
  grade TEXT NOT NULL CHECK(grade IN ('A','B','C','D')),
  family TEXT NOT NULL,
  keeper_code TEXT,
  is_active INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0,1))
);
CREATE UNIQUE INDEX idx_colours_keeper_code ON colours(keeper_code) WHERE keeper_code IS NOT NULL;
CREATE TABLE mappings(
  id TEXT PRIMARY KEY NOT NULL,
  warehouse_id TEXT NOT NULL REFERENCES warehouses(id) ON DELETE RESTRICT,
  fabric_id TEXT NOT NULL REFERENCES fabrics(id) ON DELETE RESTRICT,
  colour_id TEXT NOT NULL REFERENCES colours(id) ON DELETE RESTRICT,
  is_active INTEGER NOT NULL CHECK(is_active IN (0,1)),
  UNIQUE(warehouse_id, fabric_id, colour_id)
);
CREATE TABLE inventory(
  id TEXT PRIMARY KEY NOT NULL,
  mapping_id TEXT NOT NULL UNIQUE REFERENCES mappings(id) ON DELETE RESTRICT,
  closing INTEGER NOT NULL CHECK(closing >= 0),
  opening_cache INTEGER NOT NULL,
  last_event_at INTEGER
);
CREATE TABLE movements(
  id TEXT PRIMARY KEY NOT NULL,
  type TEXT NOT NULL CHECK(type IN ('IN','OUT','TRANSFER_OUT','TRANSFER_IN','ADJUSTMENT')),
  mapping_id TEXT NOT NULL REFERENCES mappings(id) ON DELETE RESTRICT,
  qty_delta INTEGER NOT NULL CHECK(qty_delta <> 0),
  transfer_pair_id TEXT,
  idempotency_key TEXT NOT NULL UNIQUE,
  actor_account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
  at INTEGER NOT NULL,
  note TEXT NOT NULL DEFAULT '',
  UNIQUE(transfer_pair_id, type)
);
CREATE INDEX idx_movements_mapping ON movements(mapping_id);
CREATE TABLE rolls(
  id TEXT PRIMARY KEY NOT NULL,
  mapping_id TEXT NOT NULL REFERENCES mappings(id) ON DELETE RESTRICT,
  state TEXT NOT NULL CHECK(state IN ('ACTIVE','INACTIVE')),
  count INTEGER NOT NULL CHECK(count >= 0)
);
CREATE INDEX idx_rolls_mapping_state ON rolls(mapping_id, state);
CREATE TABLE audit_log(
  id TEXT PRIMARY KEY NOT NULL,
  at INTEGER NOT NULL,
  actor_id TEXT NOT NULL,
  action TEXT NOT NULL,
  target TEXT NOT NULL,
  result TEXT NOT NULL CHECK(result IN ('ALLOWED','BLOCKED')),
  reason TEXT,
  idempotency_key TEXT
);
CREATE TABLE sessions(
  id TEXT PRIMARY KEY NOT NULL,
  account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
  issued_at_wall INTEGER NOT NULL,
  issued_at_elapsed INTEGER NOT NULL,
  boot_id TEXT NOT NULL,
  expires_at_wall INTEGER NOT NULL,
  is_active INTEGER NOT NULL CHECK(is_active IN (0,1))
);
CREATE TABLE app_settings(
  key TEXT PRIMARY KEY NOT NULL,
  value TEXT NOT NULL
);
CREATE TABLE _manifest(
  table_name TEXT PRIMARY KEY NOT NULL,
  row_count INTEGER NOT NULL,
  schema_version INTEGER NOT NULL,
  app_version TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
"""

USER_TABLES = ["accounts", "warehouses", "shed_assignments", "fabrics", "colours", "mappings",
               "inventory", "movements", "rolls", "audit_log", "sessions", "app_settings"]


def iso_z_to_epoch_millis(s: str) -> int:
    return int(datetime.strptime(s, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc).timestamp() * 1000)


def main():
    src, dst = Path(sys.argv[1]), Path(sys.argv[2])
    data = json.loads(src.read_text(encoding="utf-8"))
    if dst.exists():
        dst.unlink()
    conn = sqlite3.connect(dst)
    try:
        conn.executescript(DDL)
        conn.executemany("INSERT INTO accounts VALUES (?,?,?,?,?)",
                         [(a["id"], a["username"], a["password_pbkdf2_sha256"], a["role"], a["display_name"]) for a in data["accounts"]])
        conn.executemany("INSERT INTO warehouses VALUES (?,?,?,?,?)",
                         [(w["id"], w["name_ar"], w["name_en"], w["capacity_rolls"], w["keeper_account_id"]) for w in data["warehouses"]])
        conn.executemany("INSERT INTO shed_assignments VALUES (?,?)",
                         [(a["account_id"], a["warehouse_id"]) for a in data["shed_assignments"]])
        conn.executemany("INSERT INTO fabrics VALUES (?,?,?,?)",
                         [(f["id"], f["name_ar"], f["name_en"], f["family"]) for f in data["fabrics"]])
        conn.executemany("INSERT INTO colours VALUES (?,?,?,?,?,?,?,?)",
                         [(c["id"], c["code"], c["name_ar"], c["name_en"], c["grade"], c["family"], c["keeper_code"], int(c["is_active"])) for c in data["colours"]])
        conn.executemany("INSERT INTO mappings VALUES (?,?,?,?,?)",
                         [(m["id"], m["warehouse_id"], m["fabric_id"], m["colour_id"], int(m["is_active"])) for m in data["mappings"]])
        conn.executemany("INSERT INTO inventory VALUES (?,?,?,?,?)",
                         [(i["id"], i["mapping_id"], i["closing"], i["opening_cache"], None) for i in data["inventory"]])
        conn.executemany("INSERT INTO movements VALUES (?,?,?,?,?,?,?,?,?)",
                         [(m["id"], m["type"], m["mapping_id"], m["qty_delta"], m["transfer_pair_id"],
                            m["idempotency_key"], m["actor_account_id"], iso_z_to_epoch_millis(m["at"]), m["note"]) for m in data["movements"]])
        conn.executemany("INSERT INTO rolls VALUES (?,?,?,?)",
                         [(r["id"], r["mapping_id"], r["state"], r["count"]) for r in data["rolls"]])
        conn.executemany("INSERT INTO app_settings VALUES (?,?)",
                         [(s["key"], s["value"]) for s in data["settings"]])
        counts = {t: conn.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0] for t in USER_TABLES}
        for table, count in counts.items():
            conn.execute("INSERT INTO _manifest VALUES (?,?,?,?,?)", (table, count, 1, "1.0.0", 0))
        conn.commit()
        integrity = conn.execute("PRAGMA integrity_check").fetchone()[0]
        fk_violations = conn.execute("PRAGMA foreign_key_check").fetchall()
        print(f"integrity_check={integrity}")
        print(f"foreign_key_violations={len(fk_violations)}")
        for t, c in sorted(counts.items()):
            print(f"table {t} rows={c}")
        if integrity != "ok" or fk_violations:
            sys.exit(2)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
