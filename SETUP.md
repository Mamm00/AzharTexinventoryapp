# SETUP.md — build, run, test

## Prerequisites
- JDK 17 (Temurin recommended)
- Android SDK: platform `android-35`, build-tools `35.0.0`, platform-tools
- Gradle 8.9 on PATH (the wrapper jar is intentionally not committed)
- `sdkmanager --licenses` accepted

## Environment for release builds (optional for CI debug builds)
- `ALAZHARTEX_KEYSTORE_PATH` — absolute path to the release keystore
- `ALAZHARTEX_KEYSTORE_PASSWORD`, `ALAZHARTEX_KEY_ALIAS`, `ALAZHARTEX_KEY_PASSWORD`
If unset, release builds fall back to the debug signing config (fine for PR builds,
**not** for distribution).

## CI is the canonical builder
GitHub Actions (`.github/workflows/ci.yml`) runs, in order:
1. **static-checks** — seed sha256 pin → G7 token scan → G10 spec_lint
2. **oracles** — O2 spec_oracle (report reproducible) → D19 auditor on SQLite mirror
3. **build-and-test** — 6× generator determinism → `ktlintCheck detekt` → `:domain:test`
   (property suite ≥1,000 cases) → `:app:assembleDebug` → `:app:testDebugUnitTest` →
   `:app:lintRelease` (warnings-as-errors) → APK artifact
4. **android-connected** — API 34 x86_64 pixel_7 emulator → `:data:connectedDebugAndroidTest`
   (Room constraint tests + T25 red-team) and `:app:connectedDebugAndroidTest` (12-route
   Compose smoke test)

Locally:
```
gradle :app:assembleDebug        # debug APK
gradle :domain:test              # pure-JVM unit + property tests
gradle ktlintCheck detekt        # static gates
python3 tools/independent_checks/spec_oracle.py
bash tools/check_forbidden_tokens.sh
```

## Emulator specifics
CI uses `reactivecircus/android-emulator-runner` with `api-level: 34`, `arch: x86_64`,
`target: default`, `profile: pixel_7`. KVM is enabled via udev rule in the job. The smoke
test asserts against the EN bundle (emulator ships en-US); a device set to ar-EG will fail
the EN text assertions loudly — set the locale to English for the test run.

## Where things live
| Path | What |
|---|---|
| `seed-alazhartex.json` / `seed-report.txt` | pinned seed artefact + report (sha256-verified in CI) |
| `domain/` | pure Kotlin/JVM: stock math, gauge, CSV, RBAC, session, idempotency |
| `data/` | Room: entities, DAOs, AppDatabase + triggers, repositories, seed loader, export/import ladder |
| `app/` | Compose UI (`com.alazhartex.stock`), navigation, DI container |
| `generator/` | deterministic seed generator (`gradle :generator:run`) |
| `tools/independent_checks/` | stdlib-Python oracles (no shared code with the app) |
