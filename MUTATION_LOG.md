# MUTATION_LOG.md — mutation testing ledger (spec §9.20: ≥85% kill-rate on :domain)

## Status (honest)
- The `pitest` Gradle plugin (info.solidsoft.pitest 1.15.0) is pinned in
  `gradle/libs.versions.toml` but is **NOT applied to any module yet**.
- Reason: the domain suite includes kotest-property tests (kotest 5.9.1,
  `checkAll(1000, …)`) which do not run under pitest's JUnit4 discovery; wiring it
  unverified would either no-op silently or fail the build spuriously.
- Authoring sandbox has no JDK/Android toolchain, so a local mutation run is impossible;
  CI is the only executor and was not asked to guess.

## What is enforced instead (today)
- kotest property suite ≥1,000 sequences (`domain/src/test/.../PropertyStockMathTest.kt`)
  covering: opening+Σdeltas==closing; active+inactive==raw; closing never negative;
  replay no-op contract.
- Contract tests: UtilisationTest, CsvTest, RbacTest (27-row truth table),
  SessionPolicyTest, StockMathTest.
- CI runs `gradle :domain:test` on every push/PR.

## To finish §9.20 (next change, one PR)
1. Add `alias(libs.plugins.pitest)` to `domain/build.gradle.kts` with:
   `pitest { targetClasses.set(listOf("com.alazhartex.domain.*")); junit5PluginVersion = null;
   threads = 4; mutationThreshold = 85; coverageThreshold = 80 }`
   (JUnit4 presenter required — kotest tests are excluded from pitest's runner via
   `pitest { excludedTestClasses = listOf("*Property*") }`.)
2. First green CI run sets the baseline; survivors get strengthened tests or an entry
   here with a reason.

## Survivors triaged
None yet — no mutation run has been executed in this environment. Claiming a kill-rate
without a run would violate §9.15/§9.22 (never print a number you have not derived).
