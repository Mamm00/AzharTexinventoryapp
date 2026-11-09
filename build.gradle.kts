plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.klint) apply false
    alias(libs.plugins.spotless) apply false
}

tasks.register("forbiddenTokenScan") {
    group = "verification"
    description = "Runs tools/check_forbidden_tokens.sh (G7)"
    doLast {
        val proc = ProcessBuilder("bash", "tools/check_forbidden_tokens.sh")
            .inheritIO()
            .start()
        if (proc.waitFor() != 0) throw GradleException("forbidden token scan failed")
    }
}
