# Changelog

All notable changes to Al Azhar Tex Inventory are documented here. The format loosely
follows [Keep a Changelog](https://keepachangelog.com/); every release ships a signed APK
attached to its GitHub Release.

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
