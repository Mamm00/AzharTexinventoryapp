# AGENTS.md — AlAzharTex agent contract (IDE-independent, survives chat resets)

## Project identity
AlAzharTex — offline-first native Android fabric-roll stock manager for an Egyptian fabric merchant (Cairo, Egypt). Cairo timezone (Africa/Cairo) governs "today". Default locale ar-EG with full RTL; en fallback. No cloud sync; all data lives in an on-device Room (SQLite) store.

## Module map
- :app — Android application module (Compose UI, navigation, ViewModels, DI wiring, manifest, FileProvider).
- :domain — pure Kotlin/JVM domain logic (stock math, gauge buckets, BigDecimal utilisation, CSV escaping, RBAC policy, session rules, idempotency contract, result types). No Android imports. Unit-testable.
- :data — Room persistence: entities, DAOs, migrations, AppDatabase, repositories, TransactionRunner, import/export ladder, seed loader. Android/Room imports only here.
- :generator — pure Kotlin/JVM seed generator. Deterministic (xorshift64*), UUIDv5, canonical JSON, fixed clock. Emits seed-alazhartex.json + seed-report.txt. No Android imports.
- tools/independent_checks/ — Python 3, stdlib only, ZERO shared code with the app. spec_oracle.py (O2), bootstrap_seed.py (local artifact bootstrap; CI re-verifies), audit_checks.py (O-external for D19), csv_check.py.
- .skills/ — vendored Android/Compose skill kits (see below).
- test/ → app/src/test, androidTest/ → app/src/androidTest.

## Package naming
`com.alazhartex.stock` base; sub-packages: `.data`, `.data.local`, `.data.repo`, `.data.security`, `.data.transfer` (import/export/backup), `.domain`, `.domain.model`, `.domain.usecase`, `.ui.<screenname>`, `.ui.theme`, `.ui.navigation`, `.di` (manual DI; no Hilt to keep the dependency surface minimal and auditable).

## Build commands
- ./gradlew :app:assembleDebug
- ./gradlew :app:testDebugUnitTest
- ./gradlew :app:connectedDebugAndroidTest
- ./gradlew :app:lintRelease
- ./gradlew detekt
- ./gradlew ktlintCheck
- ./gradlew :generator:run (writes seed-alazhartex.json + seed-report.txt)
- python3 tools/independent_checks/spec_oracle.py (writes spec_report.txt)
- python3 tools/independent_checks/audit_checks.py (D19 external auditor)
Note: the Gradle wrapper jar is not committed; CI uses gradle/actions/setup-gradle. Locally, run `gradle wrapper` once or use a system Gradle. See SETUP.md.

## Test commands
- Unit: ./gradlew :domain:test :data:testDebugUnitTest :app:testDebugUnitTest
- Migration: ./gradlew :data:connectedDebugAndroidTest (Room MigrationTestHelper) or :data:testDebugUnitTest with Robolectric — CI uses the connected suite.
- UI smoke / D10: ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.alazhartex.stock.ui.smoke.AllScreensSmokeTest ; fallback chain per §9.11 (androidTest matrix → Paparazzi screenshots → recorded trace.json parity). See VERIFICATION.md for which leg actually ran.
- Property: :domain:test --tests '*PropertyTest' (kotest-property, ≥1,000 cases)
- Mutation: ./gradlew :domain:pitest (≥85% kill-rate)

## Formatting rules
ktlint (official style, android mode) + Spotless (ktlintCheck). 4-space indent, LF, final newline, max line length 120, no wildcard imports. CI fails on any violation.

## Locale rules
ar-EG is the DEFAULT locale (values-ar, RTL first-class). values/strings.xml carries EN as fallback. One key per line, identical key sets in both files; G6/i18n parity gate fails the build otherwise. Dates strictly yyyy-mm-dd (device-local zone; Africa/Cairo for ar-EG). "Today" is computed with ZoneId.of("Africa/Cairo"). NumberFormat.getInstance(Locale.getDefault()) everywhere a number reaches the UI; Western Arabic digits by default; Arabic-Indic digits only behind Settings toggle (default OFF). All stored instants are epoch millis UTC.

## Naming conventions
PascalCase for Compose screens (`StockScreen`) and data objects; camelCase for ViewModels (`stockViewModel` isn't a name — `StockViewModel` class, property `viewModel`); snake_case ONLY for SQL column names; UPPER_SNAKE_CASE for consts; UUIDv5 for stable IDs (NAMESPACE_DNS + "alazhartex.seed").

## Branch strategy & merge authority
Work happens on the session branch `arena/6dcc36ce-azhartexinventoryapp` (the harness forbids switching). The PR goes into `main`. THE HUMAN DEVELOPER MERGES. The AI never merges, never rebases main, never force-pushes main. One PR only. The PR title must contain the frozen snapshot hash (§9.12).

## Commit format (Conventional Commits)
`type(scope): imperative summary` — e.g. `feat(stock): add utilisation gauge`, `fix(data): roll back import swap on integrity failure`, `test(rbac): cover keeper-on-admin-settings denial`. Types: feat, fix, test, refactor, build, ci, docs, chore.

## Forbidden tokens (CI G7 + grep locally)
TODO, FIXME, XXX, println(, printStackTrace, runBlocking (non-test), empty catch blocks, AllowMainThreadQueries, fallbackToDestructiveMigration, hardcoded UI strings outside resources, `barcode|serial|width` in schema/UI/CSV/reports, `!!` without a preceding guard, `collectAsState()` (use collectAsStateWithLifecycle()), `_state.value = ...` (use _state.update { }), GlobalScope.launch in ViewModel, mutableStateListOf in ViewModel, LazyColumn without key+contentType, `@Query` followed by a non-transactional `@Update` in the same repository function.

## .skills/ — vendored skill kit (offline equivalent of `android skills add`)
The `android` CLI is unavailable in this environment (dl.google.com unreachable); skills were vendored manually from the master spec and public best practice. Contents:
- .skills/compose-kotlin-agent-skills/ — Compose/Kotlin banned antipatterns, architecture (UDF, hoisted state), testing sub-skills.
- .skills/android-room-migrations/ — Room schema export, migration testing rules (no fallbackToDestructiveMigration, explicit Migration objects, schema JSON diffed in CI).
- .skills/android-localisation/ — string parity AR/EN, RTL-first layouts, locale-parameterised formatting.
- .skills/android-compose-testing/ — Compose UI test semantics (testTag, semantics tree, contentDescription assertions).
- .skills/android-security/ — PBKDF2 hashing, session storage in app-private files, FileProvider path scoping, no secrets in repo.

## Project-knowledge block (pinned; single source of truth = seed-report.txt / CONTENT.md / RESOLUTIONS.md)
Seed accounts (test fixtures, hashed PBKDF2-SHA256, iterations 210,000, per-account fixed salt):
- admin / admin123 (role ADMIN; all 10 sheds; auto-signed-in; NO login screen; SalesManager = read-only label role)
- keeper1 / mostafa123 (role KEEPER; owns wh-alwa, wh-leon, wh-azouz, wh-zibda, wh-showroom)
- keeper2 / keeper2123 (role KEEPER; owns wh-625, wh-magdy, wh-wahman, wh-lycra, wh-kashry)
Shed IDs (exact): wh-alwa, wh-leon, wh-azouz, wh-zibda, wh-showroom, wh-625, wh-magdy, wh-wahman, wh-lycra, wh-kashry.
RBAC matrix: ADMIN read+write+mastersCRUD+Settings+Export/Import all 10. KEEPER read own + cross-read; write own 5; masters ADD-ONLY; never delete/regrade shared; NO Settings; NO Export/Import. SALES_MANAGER read-only.
Zero-drift invariant: for every inventory row, opening + Σ movement deltas == closing; Σ ACTIVE rolls == 68,567; raw Σ == 69,787; inactive Σ == 1,220; opening_cache == recompute for every row.
Exact seed numbers (Section 4.2 of spec; verified by three oracles): records 3,005 · sheds 10 · fabrics 61 · colours 208 (A 65, B 70, C 54, D 19) · families 11 · mappings 1,296 · inventory rows 1,296 · movements 124 (IN 40, OUT 40, TRANSFER pairs 12 → 24 events, ADJUSTMENT 20) · active rolls 68,567 (keeper1 51,333 + keeper2 17,234) · raw Σ 69,787 · inactive rows 26 · inactive rolls 1,220 · keeper colour codes 7 · wh-625: 5,255 active / 3,000 capacity = 175.2% (RED) · wh-625 mapped 180 · wh-625 active 176.
Timezone rule: Africa/Cairo for "today"; all instants epoch-millis UTC; UI dates yyyy-mm-dd.
Resolution contract: RESOLUTIONS.md is binding; contradictions are resolved there, never silently.

## Frozen snapshot (§9.12) definition
SHA-256 over canonical concatenation of: git commit SHA; seed JSON SHA-256; SHA-256 of gradle/wrapper/gradle-wrapper.properties and every build.gradle.kts; toolchain versions (./gradlew --version, android skills list where available); exact CI emulator image IDs. Any change post-green invalidates evidence; D14 fails closed.
