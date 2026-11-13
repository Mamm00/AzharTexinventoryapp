# Changelog

All notable changes to Al Azhar Tex Inventory are documented here. The format loosely
follows [Keep a Changelog](https://keepachangelog.com/); every release ships a signed APK
attached to its GitHub Release.

## [1.4.0] — 2026-11-13

### Added
- **New destination: Dashboard Settings** ("Admin"). A drawer entry and a quick-action tile
  (Tune icon) open a screen where KPIs can be entered manually when you want the board to
  see pinned figures instead of the live ones.
- **Manual overrides** for the Dashboard KPIs: Total rolls, Active rolls, Inactive rolls,
  Capacity (rolls) and Utilisation %. Persisted in **DataStore (Preferences)** — not in
  Room, not in SharedPreferences; export/import of the database does not carry them.
  The **Enable Manual Overrides** toggle gates everything; every field may be left blank
  (blank falls back to the live SQL value); a Save button validates and persists;
  "Reset to live values" clears overrides entirely.
- **Drift detection for overrides (Task 4).** When overrides are enabled the dashboard
  compares every manual value against the live SQL sums and shows a warning banner
  ("Manual overrides differ from the live database (N of 5 fields)") on any mismatch;
  when they match it shows a green "matches the live database" state. When overrides are
  disabled the classic "No drift — every row balances" / "Drift detected: N" logic applies.
- **DashboardViewModel** — the KPI state now flows from a ViewModel observing both the
  override store (DataStore) and `DbProvider.dataVersion`. Any committed write or override
  edit updates the KPIs instantly; the section above the drift banner shows all four KPIs
  (Total, Active, Capacity, Inactive).

### Fixed
- **Dashboard KPIs now aggregate the inventory ledger (the actual bug report: "Total rolls: 0"
  while Top colours showed 50/50).** The dashboard used to derive ACTIVE rolls solely from the
  derived `rolls` cache — which can silently fall out of sync with `inventory.closing`
  (`applyRollDelta` is a no-op when no ACTIVE roll row exists for a mapping). It now computes:
  Active = `SUM(inventory.closing)` (the same source Top colours reads, so the KPIs and the
  colour ranking can never disagree again), Inactive = `SUM(rolls.count) WHERE state='INACTIVE'`,
  Total = Active + Inactive, Capacity = `SUM(warehouses.capacity_rolls)`.
  The dashboard's Top sheds list uses `SUM(inventory.closing)` per warehouse as well; the
  Stock grid, Cross-View, profile and CSV stock exports keep the rolls-cache cards
  (`shedCards()`), whose external-write invalidation is pinned by `DbRefreshTest`.
- **Utilisation** is now `(Total / Capacity) × 100` everywhere on the dashboard and is guarded
  against capacity == 0 (percent renders "—", bucket null) instead of dividing by zero.
  Active = Total − Inactive is enforced by construction in the ViewModel (an explicitly typed
  Total derives Active as `Total − Inactive`).

### Notes
- Room schema unchanged (no new entities; overrides live in DataStore). `versionName` 1.4.0,
  `versionCode` 11.
- Spec deviation, deliberate: the destination list grew by one (Dashboard Settings) on top of
  the spec §4.5 baseline, per feature request.

## [1.3.6] — 2026-11-13

### Fixed
- **App-wide database invalidation.** Exporting the database or adding/bulk-assigning rolls
  never invalidated what the rest of the app had loaded: new rolls stayed invisible in
  Stock → Active Rolls and on the Dashboard KPIs until the process was killed and relaunched.
  Every repository now resolves the Room instance per call through the new `DbProvider`,
  every committed write bumps a monotonic invalidation token, and every Compose loader
  (Stock grid, Dashboard KPIs, Masters, Swatch Book, Inventory, Movements, Audit, Settings …)
  re-queries automatically when data changes anywhere in the app.
- **Settings → Import no longer kills the process.** The import path used to replace the file,
  toast "restarting…", then `Runtime.getRuntime().exit(0)` and relaunch via a clear-task
  intent — the only reason stale data ever cleared. It now rebuilds the Room instance in
  place (`DbProvider.refresh()`) and shows a confirmation snackbar; the relaunch is gone.
- **Pre-import safety backup is now complete.** The WAL was not checkpointed before the
  `safety-<ts>.db` copy, so recent commits that lived only in the WAL were silently absent
  from the "safety" snapshot. The manager now runs `PRAGMA wal_checkpoint(TRUNCATE)` first.
- Export now reloads the app-wide state as well (the export checkpoint mutates WAL state);
  previously export did nothing to the running UI.

### Added
- **Settings → Refresh database** entry — "Reload all stock, dashboard and roll data from disk".
  Closes the current Room instance, rebuilds it from `alazhartex.db`, and reloads every screen.
  Runs automatically after export/import, after bulk roll assignment ("Load palette", "Assign
  to fabric"), after inventory demo reload / danger-zone actions, and on demand.
- **`DbProvider`** (data layer): owns the process-wide `AppDatabase` singleton and defines
  database construction exactly once (migrations + trigger callback). Exposes
  `refresh(context)` (close + rebuild + re-key), `notifyDbChanged()` (same-instance
  invalidation for callers that cannot swap), and `dataVersion` (the token Compose loaders
  key on via `collectAsStateWithLifecycle`).
- Connected test **`DbRefreshTest`**: a roll inserted behind the app's back through a raw
  SQLite connection becomes visible in the Stock grid after `DbProvider.refresh()`.

### Changed
- Room construction moved from the `AppContainer` lazy val into `DbProvider`; `AppContainer`
  now exposes `database` as a live getter. `SessionManager` resolves its DAOs lazily, so a
  rebuilt database can never hand out stale DAOs to a swap.
- The v1.3.4 add-roll-screen refresh remains; it is now backed by the shared invalidation
  token, so every screen — not just the add-roll screen — reflects committed rolls immediately.
- Stock and Dashboard loaders keep their last good snapshot if a database rebuild races an
  in-flight query (first-load errors still surface as before).
- Export also re-keys the UI via the explicit `DbProvider.refresh()` call in Settings.
