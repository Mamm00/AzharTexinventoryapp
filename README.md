# AlAzharTex — Fabric-Roll Stock Manager

Offline-first native Android app for an Egyptian fabric merchant (Cairo). Every roll,
colour, shed and movement lives on-device (Room/SQLite). No account server, no cloud
sync, **no INTERNET permission**. Backups are explicit exports you keep.

## What it does
- 12 screens: Guide · Stock (utilisation gauges) · Inventory · Movements · Colours ·
  Cross-shed matrix · Dashboard · Reports (CSV + letterhead) · Masters · Audit Log ·
  Settings · Profile — full Arabic (RTL, ar-EG, default) and English locales.
- RBAC: seeded ADMIN auto-signs-in (no login screen); KEEPERs write their five sheds;
  SALES_MANAGER is read-only. Every denial is audited `BLOCKED`.
- Zero-drift invariant: `opening + Σmovement deltas == closing` per inventory row,
  checked inside every transaction.
- Import/export ladder: VACUUM INTO + sha256 sidecar out; five integrity rungs before
  any destructive import; safety copies of the previous DB kept.

## Numbers are never typed by hand
`seed-alazhartex.json` (sha256-pinned) is generated deterministically; CI re-runs the
generator six times and requires byte-identical output, re-derives every §4.2 number via
two independent Python oracles, and builds + tests the app on an API 34 emulator.

## Install (release APK)
Signed release APKs are published under **[Releases](https://github.com/Mamm00/AzharTexinventoryapp/releases)**
(tag `v*` → CI builds `:app:assembleRelease`, verifies the signature against the release
keystore, and attaches `alazhartex-vX.Y.Z.apk`). Sideload the APK to your Android device
(API 26+); the app requests **no INTERNET permission** and stores data on-device.

## Build
See **SETUP.md**. Short version (CI only — no local Android toolchain required):
`gradle :app:assembleDebug` → APK in `app/build/outputs/apk/debug/`.

## Verify
See **VERIFICATION.md** for the gate ledger and which legs have run.
Architecture decisions: **ADR.md** · binding contract: **AGENTS.md** · contradiction
rulings: **RESOLUTIONS.md** · content pins: **CONTENT.md** · plan: **PLAN.md**.

## Data location & backups
All data is in the app's private storage on the device. Export from Settings → "Export
database" regularly and keep the `.db` + `.sha256` pair somewhere safe. Import verifies
both before touching anything.

## v1.1.0 — Tiny palette, Fabrics Edit, Room v2

- **Deterministic tiny palette** — every colour's appearance is a pure function of `(family, grade, code)`
  via a pinned FNV-1a-32 → LCH(D65) → sRGB pipeline (`domain/.../PaletteResolver.kt`). `palette-report.txt`
  (208 rows) is committed and byte-compared in CI. Contrast on every colour ≥ 4.5. Nothing is stored;
  swatches re-derive at render time.
- **Colour swatches everywhere** — inventory, movements, colours, cross-view, masters, dashboard
  top-colours and the stock pickers. CSV/print output stays code-only by design.
- **New screen #13 — Fabrics Edit** (drawer, Masters button, Settings shortcut): tabs **Fabrics**
  (ADMIN add/edit/delete; KEEPER add-only; two-step delete with exact counts + type `DELETE`),
  **Types** (11 pinned families with gradient strips, member counts, A/B/C samples; ADMIN can add
  custom families, stored in-app only, hue anchor pinned to (max+25) mod 360 → nearest 5°), and
  **Colours** (code immutable for seeded colours, KEEPER gets a `K-` keeper-code prefix; delete is
  refused while any mapping/roll/movement references the colour; **Assign to fabric** bulk-creates
  zeroed mappings across all sheds in one audited transaction).
- **Palette picker** in the Stock add/adjust+ flows (search AR/EN, grade + family filters,
  pinned order grade→code, sticky selection footer).
- **Room v2** — UNIQUE fabric `(name_ar, name_en)` enforced by migration (identity-hash-stable raw
  index) and again inside the transaction; v1 installs upgrade in place. **Factory Reset is not in
  this release** (see VERIFICATION.md / ADR-016); it lands in v1.2.0.
