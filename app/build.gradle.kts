plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.klint)
}

// Single source of truth for the seed asset: the committed root seed-alazhartex.json.
val syncSeedAsset = tasks.register<Copy>("syncSeedAsset") {
    from(rootProject.file("seed-alazhartex.json"))
    into(layout.buildDirectory.dir("generated/seedAssets"))
}

android {
    namespace = "com.alazhartex.stock"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "com.alazhartex.stock"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets.getByName("main") {
        assets.srcDir(syncSeedAsset.map { it.destinationDir })
    }

    signingConfigs {
        // Release signing comes from ALAZHARTEX_* env vars in CI (SETUP.md). Debug fallback signs PR builds.
        create("release") {
            val ksPath = System.getenv("ALAZHARTEX_KEYSTORE_PATH")
            if (ksPath != null) {
                // PKCS12 is the default (our v1.0.0 keystore is PKCS12); override with
                // ALAZHARTEX_KEYSTORE_TYPE=JKS for legacy Java keystores.
                storeType = System.getenv("ALAZHARTEX_KEYSTORE_TYPE") ?: "PKCS12"
                storeFile = file(ksPath)
                storePassword = System.getenv("ALAZHARTEX_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ALAZHARTEX_KEY_ALIAS")
                keyPassword = System.getenv("ALAZHARTEX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("ALAZHARTEX_KEYSTORE_PATH") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = true
        checkGeneratedSources = false
        // Informational/packaging nags that must not gate CI; correctness checks stay on.
        // GradleDependency/AndroidGradlePluginVersion: "newer available" nags — toolchain/dep upgrades are a deliberate, reviewed task.
        // OldTargetApi: targetSdk is pinned at 34 by product decision. AppBundleLocaleChanges: sideloaded app; dynamic locale is intentional, AAB splitting N/A.
        // ProduceStateDoesNotAssignValue: false positive in this toolchain — the producer lambdas DO assign
        // `value = …` (canonical documented usage); the detector's UAST resolution fails for suspend produceState
        // here (flagged at every produceState site). Revisit on compose-bom/AGP upgrades.
        disable += setOf(
            "GradleDependency",
            "AndroidGradlePluginVersion",
            "OldTargetApi",
            "AppBundleLocaleChanges",
            "ProduceStateDoesNotAssignValue",
        )
    }
}

tasks.named("preBuild") { dependsOn(syncSeedAsset) }

dependencies {
    implementation(project(":data"))
    implementation(project(":domain"))

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.navigation.compose)
    implementation(libs.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.android.test.ext.junit)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.rules)
}
