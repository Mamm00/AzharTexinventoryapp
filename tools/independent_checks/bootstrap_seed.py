#!/usr/bin/env python3
"""bootstrap_seed.py — LOCAL-ONLY bootstrap of the seed artifacts.

This is a byte-for-byte algorithm twin of generator/src/main/kotlin/com/alazhartex/seed/SeedGenerator.kt.
It exists because this authoring sandbox has no JVM Kotlin compiler or Maven access.
CI re-runs the Kotlin generator and byte-compares against the committed artifacts
(seed-alazhartex.json, seed-report.txt); any divergence fails the build (RES-015).

NOT imported by any app code or test. Determinism rules: SHA-256/SHA-1/PBKDF2 only;
no locale, no timezone, no wall clock, no randomness.
"""
import hashlib
import json
import sys
from base64 import b64encode
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

NAMESPACE = "alazhartex.seed"
SEED_VERSION = "1.0.0"
GENERATOR_ID = f"alazhartex-generator/{SEED_VERSION}"
PBKDF2_ITERATIONS = 210_000
MOVEMENT_BASE = datetime(2026, 1, 5, 9, 17, 0, tzinfo=timezone.utc)
MOVEMENT_STEP_SECONDS = 41 * 60
NS_DNS = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"

WAREHOUSES = [
    ("wh-alwa", "مخزن الواحة", "Al-Waha Warehouse", 16000, "keeper1"),
    ("wh-leon", "مخزن ليون", "Leon Warehouse", 14000, "keeper1"),
    ("wh-azouz", "مخزن عزوز", "Azouz Warehouse", 12000, "keeper1"),
    ("wh-zibda", "مخزن زبدة", "Zibda Warehouse", 13500, "keeper1"),
    ("wh-showroom", "المعرض الرئيسي", "Main Showroom", 22000, "keeper1"),
    ("wh-625", "عنبر 625", "Shed 625", 3000, "keeper2"),
    ("wh-magdy", "مخزن مجدي", "Magdy Warehouse", 4500, "keeper2"),
    ("wh-wahman", "مخزن وهمن", "Wahman Warehouse", 5200, "keeper2"),
    ("wh-lycra", "مخزن لايكرا", "Lycra Warehouse", 7000, "keeper2"),
    ("wh-kashry", "مخزن كشري", "Kashry Warehouse", 4100, "keeper2"),
]
ACCOUNTS = [
    ("admin", "admin", "admin123", "ADMIN", "الأزهر — المدير"),
    ("keeper1", "keeper1", "mostafa123", "KEEPER", "مصطفى — أمين العنبر"),
    ("keeper2", "keeper2", "keeper2123", "KEEPER", "أمناء العنبر ٢"),
]
ACTIVE_BUDGET = {
    "wh-alwa": 11540, "wh-leon": 10208, "wh-azouz": 9672, "wh-zibda": 10341, "wh-showroom": 9572,
    "wh-625": 5255, "wh-magdy": 3018, "wh-wahman": 3144, "wh-lycra": 2851, "wh-kashry": 2966,
}
NON625_INACTIVE = {
    "wh-alwa": 3, "wh-azouz": 3, "wh-kashry": 3, "wh-leon": 3, "wh-magdy": 3,
    "wh-wahman": 2, "wh-lycra": 2, "wh-showroom": 2, "wh-zibda": 1,
}
WH625_INACTIVE_COLOURS = {45, 90, 135, 180}
FAMILIES = ["cotton", "linen", "wool", "silk", "polyester", "viscose", "denim", "velvet", "lycra", "oxford", "poplin"]
FABRIC_FAMILY_SIZES = {"cotton": 7, "linen": 6, "wool": 6, "silk": 5, "polyester": 6, "viscose": 5,
                       "denim": 6, "velvet": 5, "lycra": 6, "oxford": 4, "poplin": 5}
COLOUR_FAMILY_SIZES = {"cotton": 30, "linen": 22, "wool": 18, "silk": 22, "polyester": 24, "viscose": 14,
                       "denim": 20, "velvet": 12, "lycra": 14, "oxford": 8, "poplin": 24}
KEEPER_CODE_BY_FAMILY = {"cotton": "KH-101", "linen": "KH-102", "wool": "KH-103", "silk": "KH-104",
                         "denim": "KH-201", "velvet": "KH-202", "polyester": "KH-203"}
FABRICS_BY_FAMILY = {
    "cotton": [("Giza 90s Egyptian Cotton", "قطن جيزة 90"), ("Combed Cotton Twill", "قطن ممشط قطكي"),
               ("Brushed Flannel Cotton", "قطن فلانيل مبطّن"), ("Organic Percale", "بيركال عضوي"),
               ("Egyptian Lawn", "لتن قطن جيزة"), ("Cotton Sateen", "ساتان قطن"), ("Heavy Canvas Cotton", "كانفاس قطن ثقيل")],
    "linen": [("Irish Linen", "كتان إيرلندي"), ("Stonewashed Linen", "كتان مغسول"), ("Linen Herringbone", "كتان متعرج"),
              ("Belgian Flax", "كتان بلجيكي"), ("Linen Toweling", "كتان مناشف"), ("Ramie Blend Linen", "كتان ممزوج بالرامي")],
    "wool": [("Merino Fine", "صوف ميرينو ناعم"), ("Gabardine Wool", "صوف جابردين"), ("Harris Tweed", "تويد هاريس"),
             ("Camel Hair Wool", "صوف وبر الجمل"), ("Shetland Wool", "صوف شيتلاند"), ("Cashmere Blend", "صوف كشمير ممزوج")],
    "silk": [("Mulberry Silk Charmeuse", "حرير توت شارموز"), ("Dupioni Silk", "حرير دوبيوني"), ("Silk Organza", "حرير أورجانزا"),
             ("Tussar Silk", "حرير توسار"), ("Habotai Silk", "حرير هابوتي")],
    "polyester": [("Microfiber Polyester", "بوليستر ميكروفايبر"), ("Polyester Chiffon", "بوليستر شيفون"),
                  ("Poly Embossed Crepe", "بوليستر كريب مجسم"), ("Polyester Taffeta", "بوليستر تافته"),
                  ("Recycled PET Geo", "بوليستر معاد التدوير"), ("DTY Polyester", "بوليستر DTY")],
    "viscose": [("Viscose Twill", "فسكوز قطكي"), ("Rayon Crepe", "كريب رايون"), ("Viscose Satin", "ساتان فسكوز"),
                ("Modal Jersey Viscose", "فسكوز مودل جرسيه"), ("Bamboo Viscose", "فسكوز خيزران")],
    "denim": [("14oz Indigo Denim", "دينيم إنديجو 14 أونصة"), ("Stretch Comfort Denim", "دينيم ستريتش مريح"),
              ("Raw Selvedge Denim", "دينيم سيلفيدج خام"), ("Stonewash Denim", "دينيم مغسول بالحجر"),
              ("Black Denim 12oz", "دينيم أسود 12 أونصة"), ("Ecru Selvedge", "دينيم إكرو سيلفيدج")],
    "velvet": [("Cotton Velvet", "مخمل قطن"), ("Silk Velvet", "مخمل حرير"), ("Stretch Velvet", "مخمل ستريتش"),
               ("Devore Velvet", "مخمل ديفوريه"), ("Embossed Velvet", "مخمل مجسم")],
    "lycra": [("Sport Lycra 230gsm", "لايكرا رياضي 230"), ("Swim Lycra UV", "لايكرا سباحة UV"),
              ("Compression Lycra", "لايكرا ضغط"), ("Shiny Lycra", "لايكرا لامعة"), ("Matte Lycra", "لايكرا مطفية"),
              ("Mesh-Lycra Jacquard", "لايكرا جاكار مخرم")],
    "oxford": [("Classic Oxford 120gsm", "أكسفورد كلاسيك 120"), ("Pinpoint Oxford", "أكسفورد بينبوينت"),
               ("Oxford Chambray", "أكسفورد شامبراي"), ("Royal Oxford", "أكسفورد ملكي")],
    "poplin": [("Broadcloth Poplin", "بوبلين برودكلوث"), ("Stretch Poplin", "بوبلين ستريتش"),
               ("Wrinkle-Free Poplin", "بوبلين مقاوم للتجاعيد"), ("Poplin Stripe", "بوبلين مخطط"), ("Poplin Solid 160", "بوبلين صلب 160")],
}
HUES = [("Crimson", "قرمزي"), ("Azure", "لازوردي"), ("Emerald", "زمردي"), ("Amber", "كهرماني"),
        ("Ivory", "عاجي"), ("Charcoal", "فحمي"), ("Teal", "فيروزي"), ("Burgundy", "زركوني"),
        ("Olive", "زيتوني"), ("Indigo", "نيلي"), ("Sand", "رملي"), ("Rose", "وردي"),
        ("Gold", "ذهبي"), ("Slate", "أردوازي"), ("Terracotta", "طيني"), ("Mint", "نعناعي")]
SHADES = [("Light", "فاتح"), ("Pale", "باهت"), ("Bright", "ساطع"), ("Deep", "داكن"),
          ("Dusky", "غائم"), ("Soft", "ناعم"), ("Rich", "غني"), ("Muted", "خافت"),
          ("Warm", "دافئ"), ("Cool", "بارد"), ("Antique", "عتيق"), ("Vivid", "حي"), ("Classic", "كلاسيك")]


def sha256(s: str) -> str:
    return hashlib.sha256(s.encode("utf-8")).hexdigest()


def uuid5(name: str) -> str:
    ns = bytes.fromhex(NS_DNS.replace("-", ""))
    digest = bytearray(hashlib.sha1(ns + name.encode("utf-8")).digest()[:16])
    digest[6] = (digest[6] & 0x0F) | 0x50
    digest[8] = (digest[8] & 0x3F) | 0x80
    return str(__import__("uuid").UUID(bytes=bytes(digest)))


def pbkdf2_hash(password: str, account_id: str) -> str:
    salt = hashlib.sha256(f"{NAMESPACE}:account:{account_id}".encode("utf-8")).digest()[:16]
    dk = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, PBKDF2_ITERATIONS, 32)
    return f"{PBKDF2_ITERATIONS}${b64encode(salt).decode()}${b64encode(dk).decode()}"


def build_colours():
    codes = [f"C{i:03d}" for i in range(1, 209)]
    grade_pool = ["A"] * 65 + ["B"] * 70 + ["C"] * 54 + ["D"] * 19
    family_pool = []
    for f in FAMILIES:
        family_pool.extend([f] * COLOUR_FAMILY_SIZES[f])
    assert len(family_pool) == 208
    grade_of, family_of = {}, {}
    for idx, code in enumerate(sorted(codes, key=lambda c: sha256(f"{NAMESPACE}:grade:{c}"))):
        grade_of[code] = grade_pool[idx]
    for idx, code in enumerate(sorted(codes, key=lambda c: sha256(f"{NAMESPACE}:family:{c}"))):
        family_of[code] = family_pool[idx]
    first_of_family = {}
    for code in codes:
        fam = family_of[code]
        if fam in KEEPER_CODE_BY_FAMILY and fam not in first_of_family:
            first_of_family[fam] = code
    keeper_of = {code: KEEPER_CODE_BY_FAMILY[fam] for fam, code in first_of_family.items()}
    assert len(keeper_of) == 7
    colours = []
    for i, code in enumerate(codes):
        hue_en, hue_ar = HUES[i % 16]
        shade_en, shade_ar = SHADES[i // 16]
        colours.append({
            "code": code, "id": uuid5(f"colour:{code}"), "family": family_of[code], "grade": grade_of[code],
            "name_en": f"{shade_en} {hue_en}", "name_ar": f"{hue_ar} {shade_ar}",
            "is_active": True, "keeper_code": keeper_of.get(code),
        })
    return colours


def build_fabrics():
    fabrics, index = [], 0
    for family in FAMILIES:
        for en, ar in FABRICS_BY_FAMILY[family]:
            index += 1
            code = f"FAB-{index:03d}"
            fabrics.append({"family": family, "id": uuid5(f"fabric:{code}"), "name_en": en, "name_ar": ar})
    assert len(fabrics) == 61
    return fabrics


def sel_key(shed, natural_key):
    return sha256(f"{NAMESPACE}:select:{shed}:{natural_key}")


def build_mappings(colours, fabrics):
    out = []
    colour_by_code = {c["code"]: c for c in colours}
    f625 = fabrics[0]
    for i in range(1, 181):
        colour = colour_by_code[f"C{i:03d}"]
        nk = f"wh-625|{f625['id']}|{colour['id']}"
        out.append({"colour_id": colour["id"], "fabric_id": f625["id"], "id": uuid5(f"mapping:{nk}"),
                    "is_active": i not in WH625_INACTIVE_COLOURS, "warehouse_id": "wh-625"})
    other_sheds = [w[0] for w in WAREHOUSES if w[0] != "wh-625"]
    for shed in other_sheds:
        candidates = []
        for fi in range(1, 61):  # FAB-002 .. FAB-061 -> fabrics[1..60]
            fabric = fabrics[fi]
            for colour in colours:
                nk = f"{shed}|{fabric['id']}|{colour['id']}"
                candidates.append((sel_key(shed, nk), fabric, colour))
        candidates.sort(key=lambda x: x[0])
        chosen = candidates[:124]
        inactive_n = NON625_INACTIVE[shed]
        inactive_keys = {k for k, _, _ in chosen[:inactive_n]}
        for key, fabric, colour in chosen:
            nk = f"{shed}|{fabric['id']}|{colour['id']}"
            out.append({"colour_id": colour["id"], "fabric_id": fabric["id"], "id": uuid5(f"mapping:{nk}"),
                        "is_active": key not in inactive_keys, "warehouse_id": shed})
    assert len(out) == 1296
    assert sum(1 for m in out if not m["is_active"]) == 26
    assert sum(1 for m in out if m["warehouse_id"] == "wh-625") == 180
    assert sum(1 for m in out if m["warehouse_id"] == "wh-625" and m["is_active"]) == 176
    return out


def build_rolls(mappings):
    active_by_id = {}
    for shed in [w[0] for w in WAREHOUSES]:
        actives = sorted([m for m in mappings if m["warehouse_id"] == shed and m["is_active"]], key=lambda m: m["id"])
        budget = ACTIVE_BUDGET[shed]
        n = len(actives)
        base, rem = divmod(budget, n)
        for i, m in enumerate(actives):
            active_by_id[m["id"]] = base + (1 if i < rem else 0)
    inactives = sorted([m for m in mappings if not m["is_active"]], key=lambda m: m["id"])
    assert len(inactives) == 26
    base_i, rem_i = divmod(1220, 26)
    inactive_by_id = {m["id"]: base_i + (1 if i < rem_i else 0) for i, m in enumerate(inactives)}
    return active_by_id, inactive_by_id


def build_inventory(mappings, roll_active):
    inv = []
    for m in mappings:
        closing = roll_active.get(m["id"], 0)
        inv.append({"id": uuid5(f"inventory:{m['id']}"), "mapping_id": m["id"], "closing": closing, "opening": -1})
    return inv


def build_movements(mappings, roll_active):
    active = [m for m in mappings if m["is_active"]]
    used, out = set(), []

    def pick(code, predicate):
        keyed = sorted(((sha256(f"{NAMESPACE}:movesel:{code}:{m['id']}"), m) for m in active), key=lambda x: x[0])
        for _, m in keyed:
            if m["id"] not in used and predicate(m):
                return m
        raise RuntimeError(f"no eligible mapping for movement {code}")

    def take8_mod(h, mod):
        return int(h[:8], 16) % mod

    for i in range(1, 41):
        code = f"V{i:04d}"
        qty = 1 + take8_mod(sha256(f"{NAMESPACE}:qty-in:{code}"), 12)
        m = pick(code, lambda mm: roll_active.get(mm["id"], 0) >= qty)
        used.add(m["id"])
        out.append({"id": uuid5(f"movement:{code}"), "code": code, "type": "IN", "mapping_id": m["id"],
                    "shed_id": m["warehouse_id"], "qty_delta": qty, "transfer_pair_id": None, "order": len(out) + 1})
    for i in range(41, 81):
        code = f"V{i:04d}"
        qty = 1 + take8_mod(sha256(f"{NAMESPACE}:qty-out:{code}"), 10)
        m = pick(code, lambda mm: True)
        used.add(m["id"])
        out.append({"id": uuid5(f"movement:{code}"), "code": code, "type": "OUT", "mapping_id": m["id"],
                    "shed_id": m["warehouse_id"], "qty_delta": -qty, "transfer_pair_id": None, "order": len(out) + 1})

    groups = {}
    for m in active:
        groups.setdefault((m["fabric_id"], m["colour_id"]), []).append(m)
    sorted_groups = sorted(((k, v) for k, v in groups.items() if len(v) >= 2),
                           key=lambda kv: sha256(f"{NAMESPACE}:transfersel:{kv[0][0]}|{kv[0][1]}"))
    placed_pairs = 0
    for pair in range(1, 13):
        tcode = f"T{pair:04d}"
        vout, vin = f"V{80 + 2 * pair - 1:04d}", f"V{80 + 2 * pair:04d}"
        qty = 1 + take8_mod(sha256(f"{NAMESPACE}:qty-xfer:{tcode}"), 8)
        pair_id = uuid5(f"transfer:{tcode}")
        placed = False
        for (fab, col), members in sorted_groups:
            sorted_members = sorted(members, key=lambda m: sha256(f"{NAMESPACE}:transfer:{tcode}:{m['id']}"))
            done = False
            for j in range(len(sorted_members) - 1):
                src, dst = sorted_members[j], sorted_members[j + 1]
                if src["warehouse_id"] == dst["warehouse_id"]:
                    continue
                if src["id"] in used or dst["id"] in used:
                    continue
                if roll_active.get(src["id"], 0) < qty:
                    continue
                used.add(src["id"]); used.add(dst["id"])
                out.append({"id": uuid5(f"movement:{vout}"), "code": vout, "type": "TRANSFER_OUT", "mapping_id": src["id"],
                            "shed_id": src["warehouse_id"], "qty_delta": -qty, "transfer_pair_id": pair_id, "order": len(out) + 1})
                out.append({"id": uuid5(f"movement:{vin}"), "code": vin, "type": "TRANSFER_IN", "mapping_id": dst["id"],
                            "shed_id": dst["warehouse_id"], "qty_delta": qty, "transfer_pair_id": pair_id, "order": len(out) + 1})
                placed_pairs += 1
                done = placed = True
                break
            if done:
                break
        assert placed, f"could not place transfer pair {tcode}"
    assert placed_pairs == 12

    for i in range(20):
        code = f"V{105 + i:04d}"
        sign = 1 if i % 2 == 0 else -1
        qty = 1 + take8_mod(sha256(f"{NAMESPACE}:qty-adj:{code}"), 6)
        delta = sign * qty
        m = pick(code, lambda mm: (roll_active.get(mm["id"], 0) >= delta) if delta > 0 else True)
        used.add(m["id"])
        out.append({"id": uuid5(f"movement:{code}"), "code": code, "type": "ADJUSTMENT", "mapping_id": m["id"],
                    "shed_id": m["warehouse_id"], "qty_delta": delta, "transfer_pair_id": None, "order": len(out) + 1})
    assert len(out) == 124

    ordered = sorted(out, key=lambda mv: (mv["shed_id"], mv["mapping_id"], mv["order"]))
    import datetime as _dt
    for idx, mv in enumerate(ordered):
        mv["at"] = (MOVEMENT_BASE + _dt.timedelta(seconds=idx * MOVEMENT_STEP_SECONDS)).strftime("%Y-%m-%dT%H:%M:%SZ")
    return out


def apply_deltas_and_validate(inventory, movements):
    delta = {}
    for mv in movements:
        delta[mv["mapping_id"]] = delta.get(mv["mapping_id"], 0) + mv["qty_delta"]
    for row in inventory:
        s = delta.get(row["mapping_id"], 0)
        opening = row["closing"] - s
        assert opening >= 0, f"row {row['mapping_id']} opening {opening} < 0"
        row["opening"] = opening
    xfer = sum(mv["qty_delta"] for mv in movements if mv["type"] in ("TRANSFER_OUT", "TRANSFER_IN"))
    assert xfer == 0


def emit(colours, fabrics, mappings, roll_active, roll_inactive, inventory, movements):
    at_by_id = {}
    for mv in movements:
        at_by_id[mv["id"]] = mv["at"]

    root = {
        "accounts": [
            {"display_name": a[4], "id": a[0], "password_pbkdf2_sha256": pbkdf2_hash(a[2], a[0]),
             "role": a[3], "username": a[1]} for a in ACCOUNTS
        ],
        "audit_log": [],
        "colours": colours,
        "fabrics": fabrics,
        "inventory": [{"closing": r["closing"], "id": r["id"], "mapping_id": r["mapping_id"], "opening_cache": r["opening"]}
                      for r in sorted(inventory, key=lambda r: r["id"])],
        "mappings": [{"colour_id": m["colour_id"], "fabric_id": m["fabric_id"], "id": m["id"],
                      "is_active": m["is_active"], "warehouse_id": m["warehouse_id"]} for m in mappings],
        "meta": {"generator": GENERATOR_ID, "namespace": NAMESPACE, "seed_version": SEED_VERSION},
        "movements": [{"actor_account_id": "admin", "at": mv["at"], "id": mv["id"],
                       "idempotency_key": uuid5(f"idem:{mv['code']}"), "mapping_id": mv["mapping_id"],
                       "note": "", "qty_delta": mv["qty_delta"], "transfer_pair_id": mv["transfer_pair_id"],
                       "type": mv["type"]} for mv in sorted(movements, key=lambda mv: mv["code"])],
        "rolls": ([{"count": c, "id": uuid5(f"roll:{mid}:ACTIVE"), "mapping_id": mid, "state": "ACTIVE"}
                    for mid, c in sorted(roll_active.items())]
                   + [{"count": c, "id": uuid5(f"roll:{mid}:INACTIVE"), "mapping_id": mid, "state": "INACTIVE"}
                      for mid, c in sorted(roll_inactive.items())]),
        "sessions": [],
        "settings": [{"key": k, "value": v} for k, v in [
            ("arabic_indic_digits", "false"), ("language", "ar"), ("letterhead_address", ""),
            ("letterhead_email", ""), ("letterhead_legal_name", ""), ("letterhead_logo_uri", ""),
            ("letterhead_phone", ""), ("letterhead_tax_id", "")]],
        "shed_assignments": sorted([{"account_id": w[4], "warehouse_id": w[0]} for w in WAREHOUSES],
                                   key=lambda a: (a["account_id"], a["warehouse_id"])),
        "warehouses": [{"capacity_rolls": w[3], "id": w[0], "keeper_account_id": w[4],
                        "name_ar": w[1], "name_en": w[2]} for w in WAREHOUSES],
    }

    json_text = json.dumps(root, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n"
    json_sha = hashlib.sha256(json_text.encode("utf-8")).hexdigest()

    active_rolls = sum(roll_active.values())
    inactive_rolls = sum(roll_inactive.values())
    raw_rolls = active_rolls + inactive_rolls
    k1 = {"wh-alwa", "wh-leon", "wh-azouz", "wh-zibda", "wh-showroom"}
    k2 = {"wh-625", "wh-magdy", "wh-wahman", "wh-lycra", "wh-kashry"}
    shed_of = {m["id"]: m["warehouse_id"] for m in mappings}
    keeper1_active = sum(c for mid, c in roll_active.items() if shed_of[mid] in k1)
    keeper2_active = sum(c for mid, c in roll_active.items() if shed_of[mid] in k2)
    wh625_active = sum(c for mid, c in roll_active.items() if shed_of[mid] == "wh-625")
    wh625_capacity = next(w[3] for w in WAREHOUSES if w[0] == "wh-625")
    util = format((Decimal(wh625_active) / Decimal(wh625_capacity) * Decimal(100)).quantize(Decimal("0.1"), rounding=ROUND_HALF_UP), "f")
    assert util == "175.2", util
    m_in = sum(1 for m in movements if m["type"] == "IN")
    m_out = sum(1 for m in movements if m["type"] == "OUT")
    m_xo = sum(1 for m in movements if m["type"] == "TRANSFER_OUT")
    m_xi = sum(1 for m in movements if m["type"] == "TRANSFER_IN")
    m_adj = sum(1 for m in movements if m["type"] == "ADJUSTMENT")
    tpairs = len({m["transfer_pair_id"] for m in movements if m["transfer_pair_id"]})
    total_records = len(WAREHOUSES) + len(fabrics) + len(colours) + len(mappings) + len(inventory) + len(movements) + len(WAREHOUSES)
    assert total_records == 3005
    assert active_rolls == 68567
    assert keeper1_active == 51333
    assert keeper2_active == 17234
    assert raw_rolls == 69787
    assert inactive_rolls == 1220
    assert m_in == 40 and m_out == 40 and m_adj == 20 and m_xo == 12 and m_xi == 12 and tpairs == 12

    report = f"""alazhartex-seed-report v1
generator={GENERATOR_ID}
seed_version={SEED_VERSION}
total_records={total_records}
sheds={len(WAREHOUSES)}
fabrics={len(fabrics)}
colours={len(colours)}
colour_grade_a={sum(1 for c in colours if c['grade'] == 'A')}
colour_grade_b={sum(1 for c in colours if c['grade'] == 'B')}
colour_grade_c={sum(1 for c in colours if c['grade'] == 'C')}
colour_grade_d={sum(1 for c in colours if c['grade'] == 'D')}
colour_families={len({c['family'] for c in colours})}
fabric_colour_mappings={len(mappings)}
inventory_rows={len(inventory)}
movements={len(movements)}
movements_in={m_in}
movements_out={m_out}
movements_transfer={m_xo + m_xi}
movements_transfer_pairs={tpairs}
movements_adjustment={m_adj}
active_rolls={active_rolls}
active_rolls_keeper1={keeper1_active}
active_rolls_keeper2={keeper2_active}
raw_rolls={raw_rolls}
inactive_rolls={inactive_rolls}
inactive_mapping_rows={sum(1 for m in mappings if not m['is_active'])}
keeper_colour_codes={sum(1 for c in colours if c['keeper_code'])}
wh625_active_rolls={wh625_active}
wh625_capacity_rolls={wh625_capacity}
wh625_utilisation_pct={util}
wh625_mapped_colours={sum(1 for m in mappings if m['warehouse_id'] == 'wh-625')}
wh625_active_mappings={sum(1 for m in mappings if m['warehouse_id'] == 'wh-625' and m['is_active'])}
opening_equals_recompute=true
seed_json_sha256={json_sha}
"""
    return json_text, report


def main():
    out_dir = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    out_dir.mkdir(parents=True, exist_ok=True)
    colours = build_colours()
    fabrics = build_fabrics()
    mappings = build_mappings(colours, fabrics)
    roll_active, roll_inactive = build_rolls(mappings)
    inventory = build_inventory(mappings, roll_active)
    movements = build_movements(mappings, roll_active)
    apply_deltas_and_validate(inventory, movements)
    json_text, report = emit(colours, fabrics, mappings, roll_active, roll_inactive, inventory, movements)
    (out_dir / "seed-alazhartex.json").write_text(json_text, encoding="utf-8", newline="\n")
    (out_dir / "seed-report.txt").write_text(report, encoding="utf-8", newline="\n")
    print(f"wrote {out_dir / 'seed-alazhartex.json'}")
    print(f"wrote {out_dir / 'seed-report.txt'}")


if __name__ == "__main__":
    main()
