# SETUP.md — build, run, test

## Prerequisites
- JDK 17 (Temurin recommended)
- Android SDK: platform `android-35`, build-tools `35.0.0`, platform-tools
- Gradle 8.9 on PATH (the wrapper jar is intentionally not committed)
- `sdkmanager --licenses` accepted

## Release builds & distribution (GitHub Releases, signed APK)

Release signing is driven by 4 repository secrets (Settings → Secrets and variables →
Actions). Until they are set, `assembleRelease` falls back to the **debug** signing config
(fine for PR wiring checks, **not for distribution**).

| Secret | Value |
|---|---|
| `ALAZHARTEX_KEYSTORE_BASE64` | base64 of the release keystore (`base64 -w0 alazhartex-release.keystore`) |
| `ALAZHARTEX_KEYSTORE_PASSWORD` | keystore (PKCS12) password |
| `ALAZHARTEX_KEY_ALIAS` | `alazhartex-release` |
| `ALAZHARTEX_KEY_PASSWORD` | key password (same as store password for this keystore) |

Env vars consumed by `app/build.gradle.kts`: `ALAZHARTEX_KEYSTORE_PATH`,
`ALAZHARTEX_KEYSTORE_PASSWORD`, `ALAZHARTEX_KEY_ALIAS`, `ALAZHARTEX_KEY_PASSWORD`
(optional override: `ALAZHARTEX_KEYSTORE_TYPE`, default `PKCS12`).

CI behaviour (`.github/workflows/ci.yml`, build-and-test job):
1. **Decode release keystore** — if `ALAZHARTEX_KEYSTORE_BASE64` is set, decodes it to
   `$RUNNER_TEMP/release.keystore` and exports the env vars; otherwise the release APK
   is debug-signed and the tag-publish step is skipped.
2. **Assemble release APK** — `:app:assembleRelease` (always runs).
3. **Verify release APK signature** — `apksigner verify --print-certs`; fails the build
   if the certificate is the Android Debug one.
4. **Upload release APK** — artifact `alazhartex-release-apk` (kept 30 days).
5. On tag pushes (`v*`): **Publish GitHub Release** — creates the Release (or clobbers
   the APK on an existing one) and attaches `alazhartex-vX.Y.Z.apk`.

Release runbook for maintainers:
```
git fetch origin main && git tag v1.0.0 origin/main && git push origin v1.0.0
```
CI then builds, verifies the signature and publishes the Release. The tag triggers the
same four jobs; only the build job publishes.

Keystore custody: the keystore is **never committed** (`.gitignore`). The authoring
sandbox copy lives in `release-backup/` (git-ignored) with `CREDENTIALS.txt` (alias,
passwords, SHA-256 fingerprint). **Back it up to safe storage immediately** — GitHub
secrets cannot be read back, and losing the keystore means installed copies can never
be updated under the same signature.

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
