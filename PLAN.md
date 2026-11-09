# PLAN.md — AlAzharTex build plan

Project: AlAzharTex (Offline-first native Android fabric-roll stock manager, Egyptian fabric merchant, Cairo, Egypt)
Branch: arena/6dcc36ce-azhartexinventoryapp (session-fixed; spec's `feat/alazhartex-build` is mapped onto this branch per harness rules). One PR into main at the end. Humans merge.
Master spec: the AlAzharTex master prompt (Sections 0-9). Binding resolutions: RESOLUTIONS.md.

## Risk-tier classification of surfaces touched

| Cluster | Tier | Files / surfaces |
| --- | --- | --- |
| Docs & config (README, SETUP, .gitignore, AGENTS, ADR, CONTENT, RESOLUTIONS, VERIFICATION, MUTATION_LOG) | 0 | repo root, .skills/ |
| Build config (settings.gradle.kts, root/app/data/domain/generator build.gradle.kts, libs.versions.toml, wrapper properties) | 6 | gradle files |
| i18n resources (values/strings.xml, values-ar/strings.xml) | 0/1 | app/src/main/res/values* |
| Compose UI screens (12 screens) + previews | 1 | ui/ |
| Domain logic (stock math, gauge, BigDecimal, CSV escaper, RBAC policy, session rules, idempotency) | 2 | domain/ |
| Room data layer (entities, DAOs, migrations, repository, TransactionRunner, import/export ladder) | 3 | data/ |
| Security (PBKDF2, session reissue, idempotency store, audit, FileProvider) | 4 | data/security, session |
| Device surface (FileProvider, share sheet, document picker, emulator install) | 5 | app/ manifests, CI |
| Release (signing config, CI workflow, APK/AAB) | 6 | .github/workflows/ |

## Surfaces explicitly NOT touched
- No barcode / serial number / fabric width field anywhere (schema, UI, CSV, reports).
- No merge to main. No push to any branch other than arena/6dcc36ce-azhartexinventoryapp.
- No real credentials. Seed account passwords are test fixtures pinned by the spec and hashed with PBKDF2.

## Execution order (mirrors master prompt FINAL INSTRUCTION, adapted to sandbox)
1. Scaffold + AGENTS.md + .skills/ + PLAN.md + RESOLUTIONS.md + CONTENT.md
2. spec_lint.py; resolve contradictions; re-run until clean
3. Seed generator (Kotlin, generator/) + Python spec oracle (tools/independent_checks/spec_oracle.py). Local bootstrap of seed artifacts via checked-in algorithm twin (tools/independent_checks/bootstrap_seed.py) because no JVM Kotlin compiler exists in this sandbox; CI re-runs the Kotlin generator and byte-compares, failing on any drift. D1 three-oracle parity asserted by oracle + report.
4. Data layer (Room) + tests
5. UI screen by screen
6. CI workflow + clean-checkout gates
7. VERIFICATION.md with every derived number and hash (honest gate ledger; any gate not runnable in this sandbox is marked NOT RUN with evidence, never PASS)
8. Red-team pass; regression tests per exploit
9. Fresh-eyes pass; regression tests per mismatch
10. Open PR (snapshot hash in title). Do NOT merge.

## Sandbox reality (probed 2026-11-09, UTC)
- No JDK/Android SDK/Gradle preinstalled. `repo.maven.apache.org`, `dl.google.com`, `maven.google.com`, `repo.gradle.org`, `services.gradle.org`, `objects.githubusercontent.com`, `release-assets.githubusercontent.com`, `raw.githubusercontent.com` all unreachable (curl 000).
- Reachable: github.com, codeload.github.com, api.github.com, registry.npmjs.org, pypi.org, files.pythonhosted.org.
- Consequence: no local Compose/Room compilation, no local emulator, no local MCP mobile runner. Those gates are delegated to GitHub Actions (full network) or marked NOT RUN with evidence in VERIFICATION.md / BLOCKER.md. No gate is fabricated.
- A JDK 25 (Temurin via PyPI `jdk4py`) was installed under /home/user/.tools purely for tooling probes; the repository does not depend on it.

## Rollback / containment
- Work happens on the session branch only. Every commit is revertable via git. The generated seed artifacts are committed (seed-alazhartex.json + seed-report.txt) so CI can byte-compare.
- If any gate cannot run after N=5 repair iterations or is environment-blocked, BLOCKER.md is written and no claim of success is made.
