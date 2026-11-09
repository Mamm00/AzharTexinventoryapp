plugins {
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
