# VERIFICATION.md — gate ledger (spec §4/§9, honest statuses)

Legend: **PASS** = ran and green · **FAIL** = ran, red · **NOT RUN (reason)** = never executed
**PENDING** = wired in CI, awaiting first green run on this branch

| Gate | Status | Evidence / notes |
|---|---|---|
| Seed sha256 pin (CI static-checks) | **PASS** | a71988826a28ef283c36f5dad91c214617ec53f010fa53d7b86220272e511fe6 |
| O2 spec oracle (`spec_oracle.py`) | **PASS** | 54/54 (54 lines in `spec_report.txt`); CI re-derives and diffs |
| O-external SQLite audit (`audit_checks.py` D19) | **PASS** | 5/5 (`tools/independent_checks/audit_checks.py`) |
| Determinism ×6 (generator byte-identical) | **PASS (committed artefacts)** | 12/12 identical runs locally; CI re-asserts ×6 |
| G7 forbidden tokens (`tools/check_forbidden_tokens.sh`) | **PASS** | 46 files scanned at commit time |
| G10 spec_lint | **PASS** | `spec_lint.py` clean |
| Domain unit + property tests (kotest ≥1,000) | **PENDING (CI `:domain:test`)** | cannot run locally — no JDK 17 + Gradle 8.9 in authoring sandbox |
| Compose build `:app:assembleDebug` + R8/lint release | **PENDING (CI)** | CI is the only compiler for this branch |
| Room connected tests (API 34 emulator) | **PENDING (CI)** | `data/src/androidTest/.../DatabaseTest.kt` (FK RESTRICT, triggers, unique idempotency, conditional decrement) |
| T25 red-team regression suite | **PENDING (CI emulator)** | `data/src/androidTest/.../T25RedTeamTest.kt` (replay, shortfall rollback, capacity escape, cross-shed RBAC, anomaly fail-closed, transfer pair atomicity) |
| 12-route smoke test (D10/§9.11) | **PENDING (CI emulator)** | `app/src/androidTest/.../AllScreensSmokeTest.kt` — first CI leg of the §9.11 fallback chain; Paparazzi/trace.json parity NOT RUN (would need device or screenshot infra not yet wired) |
| Mutation testing ≥85% kill-rate (§9.20) | **NOT RUN (documented in MUTATION_LOG.md)** | pitest pinned, not applied; kotest-runner interplay unverified |
| Locale/digit audit matrix en-US/ar-EG/fr-FR (§9.21) | **NOT RUN (no emulator access in authoring sandbox; strings parity enforced at 149/149 keys in both locales + `Format.kt` uses pinned locales)** | CI emulator job exists; matrix expansion not wired |
| Fresh-eyes agent pass (T26, §9.17) | **PASS (tools)** | `tools/independent_checks/*` are independent re-derivations (stdlib Python, zero shared code): 54/54 oracle checks + 5/5 auditor checks; zero mismatches vs seed-report.txt. A no-context LLM fresh-eyes pass has NOT been executed |
| Chaos C1–C4 (§9.18) | **PARTIAL** | C1 (mid-mutation rollback) ≡ `shortfallRollsBackEverything` + transfer pair atomicity (T25). C2/C3 covered by import/export design (safety copy before swap; VACUUM INTO, never partial files) — **no dedicated fault-injection test yet: NOT RUN**. C4 (mid-audit) contract = audit-failure never masks the refusal (`TransactionRunner.writeBlockedAudit` swallows audit failure, logs, rethrows original) — asserted by code review only, no test: NOT RUN |
| On-device OEM QA (Cairo device matrix) | **NOT RUN (no devices)** | UI smoke runs on CI emulator only |
| Release signing (§RELEASE) | **PARTIAL** | `app/build.gradle.kts` release config reads `ALAZHARTEX_*` env vars; falls back to debug signing. Store keystore NOT committed/configured |
| AAB/APK size, Play-console upload | **NOT RUN (out of scope for PR)** | CI uploads debug APK artifact only |
| Drift indicator on seeded data == 0 | **PASS** | `seed_to_sqlite` mirror + `audit_checks` (active 68,567; drift rows none); in-app `driftRows()` re-checks per read path |

## CI truth (branch `arena/6dcc36ce-azhartexinventoryapp`)
- Static checks: **PASS** (run 42557369739)
- Seed oracles: **PASS** (run 42557369739)
- Build/unit/lint/detekt/ktlint: **PENDING**
- Connected (emulator): **PENDING**
Final verdict is recorded at the bottom of this file after the last CI run settles.
