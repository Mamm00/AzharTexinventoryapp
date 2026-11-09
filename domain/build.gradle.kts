plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.klint)
    alias(libs.plugins.spotless)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.kotest.property)
}

detekt {
    config.setFrom(files(rootProject.file("config/detekt/detekt.yml")))
    buildUponDefaultConfig = true
    ignoreFailures = false
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
}
