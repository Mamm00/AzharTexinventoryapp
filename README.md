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
