# AlAzharTex — Fabric-Roll Stock Manager

**The offline stock book of AlAzhar Textiles** — every fabric roll, colour, shed and movement, stored on the device that runs it. No internet, no account server, no data leaves the phone.

- **Platform:** native Android (Kotlin, Jetpack Compose, Material 3 / M3 Expressive)
- **Min SDK:** 26 (Android 8.0) · **target/compile SDK:** 34 (Android 14)
- **Distribution:** sideloaded release APK from [GitHub Releases](https://github.com/Mamm00/AzharTexinventoryapp/releases) — signed, tag-verified
- **Current version:** `v1.7.0` (versionCode 14)
- **Languages:** Arabic (`ar-EG`, default, RTL, Cairo time zone) and English (`en`)
- **Storage:** on-device SQLite via Room (`alazhartex.db`, schema v3)
- **Network access:** **none** — the app does not request the `INTERNET` permission, ever

---

## Table of contents

1. [What it does (60 seconds)](#1-what-it-does-60-seconds)
2. [Who uses it — roles](#2-who-uses-it--roles)
3. [Install & first run](#3-install--first-run)
4. [Navigation & layout (adaptive UI)](#4-navigation--layout-adaptive-ui)
5. [Screen-by-screen guide](#5-screen-by-screen-guide)
6. [Core concepts](#6-core-concepts)
7. [Roles & permissions matrix](#7-roles--permissions-matrix)
8. [Sessions & account security](#8-sessions--account-security)
9. [The database](#9-the-database)
10. [Integrity model — why numbers can't drift](#10-integrity-model--why-numbers-cant-drift)
11. [Backup & restore (export / import)](#11-backup--restore-export--import)
12. [Reports & CSV](#12-reports--csv)
13. [Notifications](#13-notifications)
14. [Permissions the app requests](#14-permissions-the-app-requests)
15. [Localization, RTL & digits](#15-localization-rtl--digits)
16. [Theming](#16-theming)
17. [Architecture for developers](#17-architecture-for-developers)
18. [Build, test & verify](#18-build-test--verify)
19. [Release process](#19-release-process)
20. [Version history](#20-version-history)
21. [Troubleshooting](#21-troubleshooting)
22. [Documentation map](#22-documentation-map)
23. [Contributing rules (non-negotiable)](#23-contributing-rules-non-negotiable)

---

## 1. What it does (60 seconds)

AlAzhar Textiles runs a fabric warehouse — **sheds (wh-alwa, wh-625 …)**, **fabrics**, **colours**, and individual **rolls**. This app replaces the paper book:

- **Stock grid** per shed — utilisation gauges, add/remove rolls, inline colour assignment.
- **Inventory ledger** — the authoritative per-mapping roll balance (`opening + Σ deltas = closing`).
- **Movements** — every IN / OUT / transfer as a signed, idempotent ledger row.
- **Colours & swatch book** — 208-seed colour catalogue with deterministic swatches, grades, families, keeper codes.
- **Cross-view** — shed × colour matrix, read-only.
- **Dashboard** — KPIs (total / active / inactive rolls, capacity, utilisation %), top sheds, top colours, drift detection, optional manual overrides (admin).
- **Reports** — Stock / Inventory / Movements / Audit / Drift as CSV, with optional company letterhead.
- **Audit log** — every attempted mutation, `ALLOWED` or `BLOCKED` with reason.
- **Backup** — one-tap export of the whole DB (with sha256 sidecar, into Downloads + share sheet), guarded five-rung import.
- **Bilingual** — Arabic-first (RTL) with English, both with full string parity.

Everything works offline. Backups are your responsibility: export regularly, keep the `.db` + `.sha256` pair somewhere safe.

## 2. Who uses it — roles

No public sign-up. Roles are seeded in the database; the **admin auto-signs-in** on cold start (no login screen).

| Role | What they can do |
|---|---|
| **ADMIN** | Everything: all sheds, masters catalogue, fabrics edit, settings, dashboard overrides, export/import, debug zone (debug builds). |
| **KEEPER** | Manage stock in **their assigned sheds**; may add fabrics/colours (add-only); **read** the rest; cannot delete/re-grade, cannot touch settings/export. |
| **SALES_MANAGER** | Read-only everywhere. |

Denials are not silent — every refused mutation is written to the audit log as `BLOCKED` with a reason.

## 3. Install & first run

1. Download `alazhartex-vX.Y.Z.apk` from **[Releases](https://github.com/Mamm00/AzharTexinventoryapp/releases)**.
2. Sideload it (Android: enable "Install unknown apps" for your browser/file manager).
3. Launch. On first run the app seeds accounts and defaults (demo dataset only where applicable — v1.2.0+ fresh installs start blank except accounts/settings).
4. On Android 13+ the app asks once for **notification permission** — optional; denying changes nothing except export/import notifications.

**Updates**: install the newer APK over the old one; data persists. Room migrations run in place (no destructive migration, ever — `fallbackToDestructiveMigration` is banned).

**Fresh install data**: accounts + `app_settings` defaults are always seeded. The demo/seed tenant loads only on the pinned seed datasets; CI guarantees byte-identical seed output.

## 4. Navigation & layout (adaptive UI)

Since **v1.6.0** the navigation adapts to window width:

- **Compact (phones, <840dp):** a bottom bar with the five high-frequency destinations — **Dashboard · Stock · Inventory · Movements · Reports**. The hamburger in the top bar (or edge swipe) opens the full drawer with all 15 destinations.
- **Expanded (tablets ≥840dp):** a permanent **navigation rail** with the same five destinations + a menu button that opens the drawer for everything else.

The dashboard is the start destination. Quick-action tiles on the dashboard reach every other destination in one tap.

Test tags (for automation): `nav_<route>` (drawer), `bottom_<route>`, `rail_<route>`.

## 5. Screen-by-screen guide

Destinations in drawer order (Arabic/English labels in-app):

### Guide
Static in-app manual: what the app is, roles, backup responsibilities, date/digit conventions, gauge thresholds, and the wh-625 over-capacity note. Read-only, no data.

### Dashboard (start)
- **Utilisation gauge** — `Total ÷ Capacity × 100`, green < 50 / amber ≤ 85 / red > 85 (wh-625 is intentionally over capacity by design).
- **KPIs** (bento 2×2 grid) — Total rolls, Active rolls (from the inventory ledger), Capacity, Inactive rolls.
- **Drift banner** — `opening + Σ deltas == closing` re-checked across all rows; "No drift" or "Drift detected: N".
- **Override state** — when manual overrides are enabled (see Dashboard Settings), the banner shows match/mismatch vs live SQL instead, and the KPIs show the pinned figures.
- **Pull to refresh** — re-reads the database locally (no network).
- **Top sheds** — per-shed `SUM(inventory.closing)` with utilisation bars.
- **Top colours** — swatches + counts from the ledger.
- **Quick actions** — one-tap to every destination.

### Dashboard Settings (Admin)
- **Enable Manual Overrides** toggle; per-field manual values (Total / Active / Inactive / Capacity / Utilisation %); blank = fall back to live SQL.
- **Save** (validated) and **Reset to live values**.
- **Drift detection for overrides**: banner lists exactly which of the 5 fields disagree with the live ledger.
- Persisted in **DataStore (Preferences)** — deliberately *not* Room, not in DB exports.

### Stock (utilisation per shed)
Per-shed cards for every mapping: fabric, colour swatch (C036-style codes), active rolls, utilisation bar, capacity.
- **Add roll(s)** — choose shed → fabric → inline colour list (search AR/EN, grade/family filters, pinned order grade→code) → qty → note → saves through the audited transaction path.
- **Adjust / Remove roll(s)** — same flow; removal refuses below zero.
- **Inline colour assignment** — assign an unmapped colour to the fabric on the spot; auto-selected after assignment.
- KEEPER sees only assigned sheds (others read-only); ADMIN sees everything.
- CSV stock exports stay rolls-cache based by design; the dashboard uses the ledger.

### Inventory
Full mapping-level ledger: per (shed, fabric, colour) — opening cache, closing, last event time. Search/filter across rows. Read-only numbers straight from Room.

### Movements
Append-only ledger of signed deltas: type (IN / OUT / adjustment / transfer pair), mapping, qty, idempotency key, actor, note, timestamp. Transfers appear as atomic pairs. The audit-companion of everything the stock grid does.

### Colours
Full colour catalogue: code, AR/EN name, grade (A/B/C/D), family, keeper code, active flag, swatch. Search + filters. ADMIN: add/edit/re-grade/delete (delete refused while referenced). KEEPER: add-only with `K-` keeper-code prefix.

### Cross-View
Shed × colour matrix; cells show mapped stock; **read-only** by design (`cross_read_only`). Use it for pick-lists and aisle planning.

### Swatch Book
- Browse the palette as a book: per shed you pick, the mapped fabrics/colours render as swatch cards (grade, family strip, AR/EN names).
- **Load swatches** (confirm dialog) — bulk-creates zeroed mappings across all sheds in one audited transaction (ADMIN; "nothing to create" is a valid outcome).
- 208 pinned seed colours derive appearance deterministically from `(family, grade, code)` — nothing about appearance is stored.

### Reports
Five CSV reports, generated on-device and shared:
| Key | Report |
|---|---|
| `stock` | Stock report |
| `inventory` | Inventory report |
| `movements` | Movements report |
| `audit` | Audit report |
| `drift` | Drift report |

- **Letterhead**: optional company letterhead on generated reports — legal name / address / phone saved in app settings (Settings → Letterhead), toggled per report; plain text header if unset, with an optional logo file name.
- Export happens as `.csv` via the share sheet (v1.7.0: with proper `ClipData`).

### Masters
Catalogue maintenance:
- **Sheds** — list; add/edit entry (ADMIN); shed capacity.
- **Fabrics** — list; add/edit; unique `(name_ar, name_en)`.
- **Colours** — list; add/edit/re-grade.
- **Mappings** — shed × fabric × colour links; add mapping (zeroed inventory row); edit entry toggle; delete refused while referenced (RESTRICT).
- **Fabrics Edit** (own screen, #13) — ADMIN: full add/edit/delete incl. **two-step delete** with exact counts and type-`DELETE` confirmation; **Types** tab (11 pinned families with gradient strips + member counts; ADMIN may add custom families); **Assign to fabric** (bulk-create zeroed mappings across all sheds, one audited transaction).

### Audit
Full audit log viewer with filters (All / ALLOWED / BLOCKED), reason column, idempotency key, actor, action, target, timestamp. This is the receipt for every attempted mutation — including refused ones and session refreshes (`session.reissue` / `session.refresh`).

### Settings
- **Profile / Letterhead** — legal name, address, phone, logo file name (renders as report letterhead).
- **Export database** — see [§11](#11-backup--restore-export--import). Produces `alazhartex-export-*.db` + `.sha256`.
- **Import database** — pick a `.db` (and optionally sidecar); five-rung verification ladder, preview, explicit overwrite confirmation, safety copy kept.
- **App permissions** (v1.5.0) — live status + one-tap Grant for notifications / media / all-files access.
- **Refresh database** (v1.3.6) — force-close & rebuild the Room instance from disk (for edits made outside the app, e.g. `adb`/desktop tooling).
- **Digits** — Western vs Arabic-Indic numerals (default Western).
- **Debug danger zone** (debug builds only) — reload demo dataset, wipe all, debug adds — used by the instrumented test-suite.

### Profile
Who you're signed in as (display name, role), assigned sheds, session expiry countdown. No password change UI in-app (credentials are managed by reseeding / admin tooling).

## 6. Core concepts

- **Mapping** — the atom: `warehouse × fabric × colour`. Everything (inventory row, rolls cache, movements, swatches) hangs off a mapping.
- **Ledger truth vs rolls cache** — `inventory.closing` is authoritative; `rolls(ACTIVE/INACTIVE)` is a derived cache. Dashboard/Top-colours read the ledger; Stock grid, profile and CSV stock exports read the cache (pinned by tests).
- **Utilisation** — `(total ÷ capacity) × 100`; 0 capacity renders "—".
- **Idempotency keys** — every mutation carries a key; replay returns `AlreadyApplied` instead of double-counting.
- **Zero drift** — `opening + Σ qty_delta == closing`, re-checked inside every transaction; drift report audits it globally.

## 7. Roles & permissions matrix

Derived from `domain/rbac/Rbac.kt` (enforced in the transaction layer, not just the UI):

| Capability | ADMIN | KEEPER | SALES_MANAGER |
|---|---|---|---|
| Read sheds | ✅ all | ✅ assigned (all read in practice) | ✅ all |
| Write stock in assigned shed | ✅ | ✅ | ❌ |
| Write stock in other shed | ✅ | ❌ BLOCKED | ❌ BLOCKED |
| Masters add (fabric/colour) | ✅ | ✅ | ❌ |
| Masters edit / delete / re-grade | ✅ | ❌ | ❌ |
| Fabrics Edit (full) | ✅ | add-only | ❌ |
| Custom families | ✅ | ❌ | ❌ |
| Bulk-assign to fabric | ✅ | ✅ | ❌ |
| Dashboard Settings overrides | ✅ | ❌ | ❌ |
| Export / import database | ✅ | ❌ | ❌ |
| Settings (letterhead, digits, permissions) | ✅ | ❌ | ❌ |
| Audit log view | ✅ | ✅ | ✅ (read) |

Every denial → `BLOCKED` audit row with reason.

## 8. Sessions & account security

- Passwords stored as **PBKDF2-SHA256** hashes (`data/security/PasswordHasher.kt`).
- **ADMIN**: auto-issues a session on cold start; on foreground, missing/expired → silent re-issue + `session.reissue` audit row; mutation past 12h → silent refresh + `session.refresh` audit row, then proceeds.
- **KEEPER**: strict 12h, no refresh; expired → refusal. Anomaly → refusal always.
- Sessions carry wall-clock + elapsed-realtime + `boot_id` — clock tampering invalidates them.

## 9. The database

One file: **`alazhartex.db`** (Room, schema **v3**, `exportSchema = true`, schema JSON committed under `data/schemas/`).

| Table | Purpose |
|---|---|
| `accounts` | Users (username, PBKDF2 hash, role, display name, active flag) |
| `warehouses` | Sheds (AR/EN names, capacity, keeper_account_id) |
| `shed_assignments` | Account ↔ shed links |
| `fabrics` / `colours` | Catalogue; colours carry grade/family/keeper code/custom hex |
| `mappings` | warehouse × fabric × colour |
| `inventory` | **Ledger**: mapping_id, closing, opening_cache, last_event_at |
| `movements` | Signed deltas; `transfer_pair_id`; `idempotency_key` |
| `rolls` | Derived ACTIVE/INACTIVE counts cache |
| `audit_log` | Every mutation attempt (`ALLOWED`/`BLOCKED`, reason, key) |
| `sessions` | Sessions (wall + elapsed + boot_id, expiry, active flag) |
| `app_settings` | Key/value (letterhead, digits, …) |
| `_manifest` | Per-table row counts + schema/app version — used by import R5 |

Migrations: `MIGRATION_1_2` (unique fabric name pair), `MIGRATION_2_3` (`custom_hex`, `notes`) — and **never** a destructive one.

## 10. Integrity model — why numbers can't drift

1. **One write path** — every mutation goes through `TransactionRunner.withAudit()`: single Room transaction → mutation → invariant re-check **inside the same transaction** → exactly one `ALLOWED` audit row → commit. Any exception → full rollback + one `BLOCKED` audit row.
2. **Conditional decrement** — stock can never go negative (`conditionalDecrement`, atomic).
3. **Idempotency** — `movements`/`audit_log` checked by `idempotency_key` before applying.
4. **FK RESTRICT everywhere** + `PRAGMA foreign_keys = ON` per connection (Room callback).
5. **Triggers** for what Room can't express: `qty_delta ≠ 0`, `grade ∈ A/B/C/D`.
6. **Unique indexes**: fabrics `(name_ar, name_en)`; movements/audit idempotency (via query checks).
7. **`PRAGMA application_id = 'AZTH'`** (1096438856) pins the file to this app — import R4 refuses anything else.
8. **WAL journal**: the DB runs in WAL mode; export checkpoint-truncates before snapshotting (v1.7.0).

## 11. Backup & restore (export / import)

### Export (Settings → Export database)
1. `_manifest` row-count table rewritten inside a transaction.
2. `PRAGMA wal_checkpoint(TRUNCATE)` (drained via `query()` — v1.7.0 root-cause fix).
3. Snapshot via **`VACUUM INTO`** (fallback: checkpoint+copy + `integrity_check` verification — v1.7.0).
4. **`.sha256` sidecar** written next to the file.
5. File lands in the app-private `files/exports/` **and is copied to the shared Downloads folder** (MediaStore on API 29+, no permission needed) — plus an `application/octet-stream` share sheet for sending it anywhere (v1.7.0; `ClipData` set).
   - On API ≤ 28 the Downloads copy uses the legacy external-storage write permission.
6. You also get an export-ready notification (if granted).

### Import (Settings → Import database)
Five integrity rungs, in order — any failure aborts before anything is touched:

| Rung | Check |
|---|---|
| R1 | sha256 of the file vs `.sha256` sidecar (if provided) |
| R2 | SQLite magic header |
| R3 | `PRAGMA integrity_check` |
| R4 | `application_id` == 'AZTH', `user_version` in supported range, FK re-check |
| R5 | per-table `_manifest` row counts match the file |

Then: **preview** (counts, app/schema version) → **explicit confirmation dialog** → safety copy of the current DB (keeps last 3 in `files/backups/`) → **atomic replace** → import-complete notification.

**If export used to "do nothing"**: that was v≤1.6.x — the checkpoint ran on the wrong SQLite path and threw before writing. Fixed in v1.7.0; the snackbar now also shows the real error message if anything still fails.

## 12. Reports & CSV

In-app CSV generation (`data/csv/CsvExport.kt`); on-device; shared via share sheet. Five reports: `stock`, `inventory`, `movements`, `audit`, `drift`. Optional **letterhead** (legal name/address/phone + optional logo file name) configured in Settings → Letterhead, toggled per report. Codes only — no swatch images in CSV by design.

## 13. Notifications

Channel `alazhartex.general` (created at app start):
- **Export ready** (ID 1001) — tap → opens the app.
- **Import complete** (ID 1002) — tap → opens the app.
- Android 13+ (API 33+) asks once at cold start; denying is non-fatal (snackbars remain).
- No background work, ever — notifications are posted synchronously from the triggering action.

## 14. Permissions the app requests

| Permission | Why | Notes |
|---|---|---|
| `POST_NOTIFICATIONS` | export/import notifications | API 33+; asked once; optional |
| `READ_MEDIA_IMAGES / VIDEO / AUDIO` | media-aware file pickers | API 33+ granular |
| `READ_MEDIA_VISUAL_USER_SELECTED` | partial photo access | API 34+ |
| `READ_EXTERNAL_STORAGE` (≤32) / `WRITE_EXTERNAL_STORAGE` (≤29) | legacy file access | maxSdk'd |
| `MANAGE_EXTERNAL_STORAGE` | "All files access" for the export flow on API 30+ | **sideload-only** distribution; Play would require a declaration form. Grantable only from system toggle: Settings → App permissions → All files access |
| *(none for DB export/import)* | SAF + MediaStore Downloads | no permission needed |
| `INTERNET` | — | **never declared** |

All grants are user-driven (Settings → App permissions card shows live Granted/Not-granted status).

## 15. Localization, RTL & digits

- Arabic (`ar-EG`) is the default locale; English (`en`) full parity; dynamic locale attach at startup (`attachBaseContext`).
- RTL mirroring throughout (`supportsRtl=true`); drawer/bottom bar/rail mirror automatically.
- Dates: `yyyy-mm-dd`; "today" is Cairo time.
- Western digits by default; **Arabic-Indic digits** toggle in Settings (affects all numerals incl. KPIs).
- CSV/letterhead: codes and EN digits preferred for machine-readability.

## 16. Theming

Material 3 with an M3-Expressive-leaning shape scale (16/24/32dp via one `Shapes` token set) and, on **API 31+**, **dynamic colour** from the wallpaper — the AzharTex brand scheme (`AzharGreen` + `Sand`) is the guaranteed fallback. Semantic gauge colours (green/amber/red) are pinned and never dynamic. Light and dark schemes both tuned; contrast-checked palette (grade swatches ≥ 4.5).

## 17. Architecture for developers

```
alazhartex/
├── app/        # Compose UI: navigation, screens, viewmodels, DI container (AppContainer), notifications, permissions
├── data/       # Room (entities/Daos/DbProvider/migrations), repositories, seed loader, export/import, CSV, session, security
├── domain/     # Pure Kotlin contracts: RBAC, session policy, utilisation gauge, palette resolver, stock math, idempotency
├── generator/  # Deterministic demo-seed generator (CI-verified byte-identical)
└── tools/      # Verification oracles (Python) used by CI
```

Key decisions:
- **`AppContainer`** — tiny manual DI graph; no framework.
- **`DbProvider`** — process-wide Room owner + monotonic `dataVersion` invalidation token; screens re-key queries off it.
- **`TransactionRunner`** — the only way to write (see §10).
- **`ShedRollsSource`** — enum selecting ledger aggregation (dashboard/top sheds) vs rolls cache (Stock grid/CSV).
- **M3 Expressive-ish** — custom `Shapes`, dynamic color w/ brand fallback, bento KPI grid, pull-to-refresh.
- Deterministic **FNV-1a → LCH(D65) → sRGB** swatch pipeline (`domain/palette/PaletteResolver.kt`) — palette is a pure function, never stored.
- Seed pinned by sha256; generator re-run ×6 in CI; two independent Python oracles re-derive every number.

## 18. Build, test & verify

**No local Android toolchain is required — CI is the build/test gate.** See **SETUP.md** for the full matrix.

| Gate | What it checks |
|---|---|
| Static checks | seed pin, schema export (G5/G7), spec lint |
| Seed oracles | O2 oracle, determinism ×6, D19 auditor |
| Build job | `:app:assembleRelease`, unit tests, `lintRelease` (warnings-as-errors), detekt, ktlint |
| Connected tests | API 34 emulator: Room constraints, 12-screen smoke, DbRefresh/ExportFlow regression, debug-zone tests |

Schema exports are committed (`data/schemas/`); any entity change without a migration fails CI. detekt is informational.

## 19. Release process

1. Work lands on an `arena/*` branch → PR → all CI jobs green → merge to `main`.
2. Maintainer tags `vX.Y.Z` → CI builds the signed release APK (signature verified against the release keystore) and **auto-publishes the GitHub Release** with `alazhartex-vX.Y.Z.apk`.
3. Users sideload the new APK over the old — data persists.

Current: **v1.7.0** — https://github.com/Mamm00/AzharTexinventoryapp/releases/tag/v1.7.0

## 20. Version history

| Version | Date | Highlights |
|---|---|---|
| 1.7.0 | 2026-11-14 | **DB export root-cause fix** (checkpoint pragma) · export copied to Downloads (MediaStore) + `.sha256` · `integrity_check`-verified fallback · detailed error snackbar · `ExportFlowTest` pins it |
| 1.6.0 | 2026-11-14 | Adaptive navigation (bottom bar / rail) · M3 Expressive shape scale · dynamic color (API 31+) · bento dashboard KPIs · pull-to-refresh · nav-menu a11y string |
| 1.5.0 | 2026-11-13 | Files/media permissions + “App permissions” card · export/import notifications · cold-start notification prompt · GrantPermissionRule in tests |
| 1.4.0 | 2026-11-13 | Dashboard Settings (Admin): manual KPI overrides + drift detection · dashboard KPIs now aggregated from the inventory ledger · utilisation ÷0 guard |
| 1.3.6 | 2026-11-13 | App-wide DB invalidation (`DbProvider`) — new rolls visible without restart |
| ≤1.3 | — | See **CHANGELOG.md** (1.1.0: Fabrics Edit + Room v2 + tiny palette; 1.2.0: blank fresh installs; 1.3.0: Room v3; 1.3.6 above) |

Full details: **CHANGELOG.md**.

## 21. Troubleshooting

| Symptom | Fix |
|---|---|
| Export "does nothing" (pre-v1.7.0) | Upgrade to v1.7.0+. The checkpoint ran on the wrong SQLite path and threw; now fixed + surfaced. |
| Export fails with a message | Read the snackbar — it shows the real error (v1.7.0+). Also see `Logcat` tag `AlAzharTex`. |
| New rolls not visible after external DB edit | Settings → **Refresh database** (or restart the app). In-app writes invalidate themselves automatically. |
| Notifications don't appear | Settings → App permissions → Notifications → Grant; check system channel (`alazhartex.general`) isn't silenced. |
| "All files access" won't grant | Only via system toggle: Settings → App permissions → All files access → system screen. Button deep-links there. |
| Swatch colours look off | Swatches derive from `(family, grade, code)` — they're pinned by design; report to maintainer, don't hand-edit. |
| Import rejected | The ladder tells you the rung — R1 hash/sidecar, R3 integrity, R4 application_id, R5 manifest counts. If legitimate, re-export from the source device. |
| Numbers differ between Stock grid and Dashboard | By design: Stock grid reads the rolls cache; Dashboard reads the ledger (see §6). Reconcile with Drift report. |
| Wrong language/digits | Settings → Digits; locale follows system (or ar-EG default); force via system per-app language. |
| "wh-625 over capacity" | Deliberate — it's the scale-test tenant, seeded over capacity by design. |

## 22. Documentation map

| File | Contents |
|---|---|
| `SETUP.md` | Build & environment setup (CI-only toolchain) |
| `VERIFICATION.md` | Gate ledger — which verification legs have actually run |
| `CHANGELOG.md` | Every release, in Keep-a-Changelog style |
| `MASTER_SPEC.md` / `spec_report.txt` | Binding product spec + audit |
| `ADR.md` | Architecture decision records (incl. ADR-016 Factory Reset) |
| `AGENTS.md` | Agent/contributor operating contract |
| `RESOLUTIONS.md` | Contradiction rulings |
| `CONTENT.md` | Content pins (brand, palette, strings) |
| `PLAN.md` / `PLAN.sha256` | Execution plan + pin |
| `MUTATION_LOG.md` | Ledger of spec mutations |
| `seed-report.txt` / `palette-report.txt` | Byte-compared generator outputs |

## 23. Contributing rules (non-negotiable)

1. **Offline-first forever.** No `INTERNET` permission, no telemetry, no cloud, no analytics. Ever.
2. **One write path.** All mutations through `TransactionRunner`; UI never writes DAO directly.
3. **No destructive migrations.** `fallbackToDestructiveMigration` is banned; every schema change ships a migration + updated `data/schemas/`.
4. **Ledger is truth.** Don't "fix" cache drift by mutating the cache; fix the aggregation or the invariant.
5. **Audit everything.** New mutation? New action name, allowed on both paths, `BLOCKED` row with reason.
6. **Idempotency.** Every mutating flow accepts an idempotency key and honors `AlreadyApplied`.
7. **CI is the gate.** No local release claims; merge only on green CI; tags are signed; releases are auto-published.
8. **Spec wins.** `MASTER_SPEC.md` + `RESOLUTIONS.md` are binding; deviations are documented, never silent.
9. **Numbers are never typed.** Derive them (oracles), pin them (sha256), verify them (CI).

---

**AlAzhar Textiles** · internal tool · proprietary (no license granted) · © AlAzhar Textiles, Cairo
Made with Kotlin, Compose, Room — and a healthy paranoia about drifting stock numbers.
