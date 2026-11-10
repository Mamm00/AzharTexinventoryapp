plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.klint)
}

android {
    namespace = "com.alazhartex.stock.data"
    compileSdk = libs.versions.compile.sdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.min.sdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
    testOptions { unitTests { isIncludeAndroidResources = true } }
    lint {
        // Informational/packaging nags only; correctness lint checks stay enabled.
        disable += setOf(
            "GradleDependency",
            "AndroidGradlePluginVersion",
            "OldTargetApi",
        )
    }
}

dependencies {
    api(project(":domain"))
    api(libs.room.runtime)
    api(libs.room.ktx)
    implementation(libs.sqlite.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.coroutines.android)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.android.test.ext.junit)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.rules)
    androidTestImplementation(libs.room.testing)
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
