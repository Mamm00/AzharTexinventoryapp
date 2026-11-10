# CONTENT.md — pinned content (spec §9.14). Single source for names that must NOT be invented at build time.
All names below appear in BOTH values/strings.xml (en) and values-ar/strings.xml (ar). CSV column sets are read by code from one constant (CsvColumnSets.kt); never inline.

## 1. Colour families (11) — AR + EN (family key → names)
family.cotton   — en: Cotton   | ar: قطن
family.linen    — en: Linen    | ar: كتان
family.wool     — en: Wool     | ar: صوف
family.silk     — en: Silk     | ar: حرير
family.polyester— en: Polyester| ar: بوليستر
family.viscose  — en: Viscose / Rayon | ar: فسكوز / رايون
family.denim    — en: Denim    | ar: دينيم
family.velvet   — en: Velvet   | ar: مخمل
family.lycra    — en: Lycra / Spandex | ar: لايكرا / سباندكس
family.oxford   — en: Oxford   | ar: أكسفورد
family.poplin   — en: Poplin   | ar: بوبلين
Family sizes (fabrics, 61 total): Cotton 7, Linen 6, Wool 6, Silk 5, Polyester 6, Viscose 5, Denim 6, Velvet 5, Lycra 6, Oxford 4, Poplin 5.
Family sizes (colours, 208 total): Cotton 30, Linen 22, Wool 18, Silk 22, Polyester 24, Viscose 14, Denim 20, Velvet 12, Lycra 14, Oxford 8, Poplin 24. (Sums are exact; generator asserts.)

## 2. Keeper colour codes (7) — pinned to owners (spec §4.2 "keeper colour codes 7")
Keeper1 (mostafa123) owns wh-alwa, wh-leon, wh-azouz, wh-zibda, wh-showroom:
- KH-101 Royal Crimson — قرمزي ملكي — attached to first Cotton colour (canonical order)
- KH-102 Nile Cobalt   — كحلي النيل   — attached to first Linen colour
- KH-103 Oasis Sand    — رمل الواحة   — attached to first Wool colour
- KH-104 Palm Green    — أخضر النخل   — attached to first Silk colour
Keeper2 (keeper2123) owns wh-625, wh-magdy, wh-wahman, wh-lycra, wh-kashry:
- KH-201 Cairo Charcoal— فحمي القاهرة — attached to first Denim colour
- KH-202 Saffron Dune  — زعفراني الكثبان — attached to first Velvet colour
- KH-203 Delta Ivory   — عاجي الدلتا  — attached to first Polyester colour
Exactly 7 colour rows carry a keeper_code; all other colours carry NULL. Generator asserts count == 7 and owner match.

## 3. AlAzharTex letterhead (blank by default; filled via Settings → AlAzharTex profile)
logo_uri (blank) · legal_name (blank) · address (blank) · phone (blank) · email (blank) · tax_id (blank) · brand: AlAzharTex — الأزهر تكستيل
Reports render the full letterhead ONLY when legal_name is non-empty; otherwise a text-only header with the brand name. Letterhead fields are NOT seeded (merchant fills them).

## 4. Guide screen — six i18n-keyed paragraphs (GuideContent.kt → List<GuideSection>, @StringRes keys)
guide_p1 (What this app is): AlAzharTex is the offline stock book of AlAzhar Textiles — every fabric roll, colour, shed and movement is stored on this device. No internet, no account server, no data leaves the phone.
guide_p2 (Roles): The admin manages every shed, the masters catalogue, settings and backups. Keepers manage the sheds assigned to them and may read the others. The sales manager reads everything and changes nothing.
guide_p3 (Offline guarantee): Everything works with zero connectivity. Backups are your responsibility: export the database from Settings and keep the file plus its sha256 sidecar somewhere safe.
guide_p4 (Export / Import): Export writes a consistent snapshot (VACUUM INTO) with a sha256 sidecar and a share/save sheet. Import runs a five-rung integrity ladder (hash, SQLite header, PRAGMA integrity_check, schema+app version, per-table manifest counts) before you confirm a destructive overwrite; a safety copy of the current database is kept.
guide_p5 (Dates & numbers): Dates are always yyyy-mm-dd. "Today" is Cairo time. Numbers use Western digits by default; you may switch to Arabic-Indic digits in Settings. A utilisation gauge is green below 50 %, amber from 50 % to 85 %, red above 85 % — and wh-625 is deliberately over capacity as the scale test.
guide_p6 (FAQ): "Where is my data?" — In the app's private storage on this device only; export regularly. That single answer covers 95 % of support calls.

## 5. Settings → AlAzharTex profile fields (all optional)
profile.legal_name · profile.address · profile.phone · profile.email · profile.tax_id · profile.logo_uri — strings in both locales; empty default; never seeded with fabricated values.

## 6. Reports — CSV column sets (pinned; code constant CsvColumnSets)
stock:     shed_id, shed_name_en, shed_name_ar, capacity_rolls, active_rolls, utilisation_pct
inventory: warehouse_id, fabric_en, fabric_ar, colour_en, colour_ar, grade, family, active_rolls, inactive_rolls, opening, closing
movements: id, type, warehouse_id, mapping_id, qty_delta, at_iso_utc, transfer_pair_id, note
audit:     id, at_iso_utc, actor, action, target, result, reason
drift:     warehouse_id, mapping_id, opening, sum_deltas, closing, drift
BOM (U+FEFF) exactly once per file; formula-injection escaping per §9.13; file names alazhartex-<report>-<yyyymmdd-HHmmss>.csv.

## 7. Number/seed format pins
Seed JSON: canonical (sorted keys, UTF-8, LF, no trailing spaces), Western digits always; UUIDv5 NAMESPACE_DNS+"alazhartex.seed"; timestamps ISO-8601 Z in JSON, epoch millis UTC in Room; NO Locale/TimeZone/currentTimeMillis/Instant.now/Math.random in any seed field.

## 8. Sheds (10, exact IDs) and capacities (seed design values; wh-625 pinned by spec)
wh-alwa 16,000 · wh-leon 14,000 · wh-azouz 12,000 · wh-zibda 13,500 · wh-showroom 22,000 (keeper1)
wh-625 3,000 (spec-pinned) · wh-magdy 4,500 · wh-wahman 5,200 · wh-lycra 7,000 · wh-kashry 4,100 (keeper2)

## 9. Seed accounts (test fixtures; PBKDF2-SHA256, 210,000 iterations; per-account salt = first 16 bytes of SHA-256("alazhartex.seed:account:<id>"))
admin / admin123 (ADMIN, all 10) · keeper1 / mostafa123 (KEEPER, 5 sheds above) · keeper2 / keeper2123 (KEEPER, 5 sheds above). No SALES_MANAGER account is seeded.

## 10. Brand
App name: AlAzharTex — الأزهر تكستيل. Brand colour accent: Azhar green #0F5132 with sand #D4A017 accent (decorative only; never sole meaning carrier).
