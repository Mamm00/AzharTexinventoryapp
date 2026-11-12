plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.klint)
    application
}

kotlin { jvmToolchain(17) }

application {
    mainClass.set("com.alazhartex.seed.SeedGeneratorKt")
}

dependencies {
    implementation(project(":domain"))
}
