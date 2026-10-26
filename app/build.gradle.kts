import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use(::load)
    }
}
// NAVER Maps client ID (ncpKeyId). It ships inside the APK; NAVER accepts it only for registered package names.
val naverMapClientId = providers.gradleProperty("NAVER_MAP_CLIENT_ID")
    .orElse(providers.environmentVariable("NAVER_MAP_CLIENT_ID"))
    .orNull
    ?: localProperties.getProperty("naver.mapClientId", "")
val escapedNaverMapClientId = naverMapClientId
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

// Device deployments opt in so a missing local setting cannot silently ship a blank map.
if (providers.gradleProperty("requireMapKey").orNull == "true") {
    require(naverMapClientId.isNotBlank()) {
        "NAVER map configuration is required for this APK. Configure naver.mapClientId or NAVER_MAP_CLIENT_ID."
    }
}

// Explicit provenance, separate from versionCode and the installed APK digest.
// Restrict caller input so build metadata cannot introduce arbitrary log content.
val diagnosticsBuildId = providers.gradleProperty("diagnosticsBuildId")
    .orElse("local-unidentified").get()
    .takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,80}")) } ?: "local-unidentified"

// Optional package suffix for a side-by-side debug install; validated so it stays a legal id part.
val debugApplicationIdSuffix = providers.gradleProperty("debugApplicationIdSuffix").orNull
    ?.takeIf { it.matches(Regex("\\.[a-z][a-z0-9_]{0,30}")) }
// Optional launcher label for that install, e.g. -PdebugAppLabel="GEO-X 재설계"; '&', '<' and quotes are refused.
val debugAppLabel = providers.gradleProperty("debugAppLabel").orNull
    ?.trim()?.takeIf { it.isNotEmpty() && it.length <= 30 && it.none { c -> c in "&<>\"'" } }

android {
    namespace = "com.example.jetsoncontroller"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.jetsoncontroller"
        minSdk = 31
        targetSdk = 37
        versionCode = 26
        versionName = "1.18.0"
        manifestPlaceholders["appLabel"] = "@string/app_name"

        buildConfigField("String", "NAVER_MAP_CLIENT_ID", "\"$escapedNaverMapClientId\"")
        buildConfigField("String", "DIAGNOSTICS_BUILD_ID", "\"$diagnosticsBuildId\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Optional isolated test package preserves a device's existing test installation.
        testApplicationId = providers.gradleProperty("testApplicationId")
            .getOrElse("com.example.jetsoncontroller.test")
    }

    buildTypes {
        debug {
            // Side-by-side debug install for a phone whose existing build was signed with another
            // key, e.g. -PdebugApplicationIdSuffix=.redesign. Unset keeps the normal package.
            if (debugApplicationIdSuffix != null) {
                applicationIdSuffix = debugApplicationIdSuffix
                versionNameSuffix = "-side"
                // A distinct launcher label keeps the two installs apart ('&' is not XML-safe here).
                manifestPlaceholders["appLabel"] = debugAppLabel ?: "GEO-X"
            }
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    lint {
        // Navigation 2.8.5's type-safe route detectors cannot load their UtilKt
        // helper under AGP 9.3.1. Keep every unrelated lint check enabled.
        disable += "WrongNavigateRouteType"
        disable += "WrongPopBackStackRouteType"
        disable += "WrongStartDestinationType"
        disable += "MissingSerializableAnnotation"
        disable += "MissingKeepAnnotation"
        disable += "ComposableDestinationInComposeScope"
        disable += "ComposableNavGraphInComposeScope"
        disable += "UnrememberedGetBackStackEntry"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.google.mlkit.barcode.scanning)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.core)
    implementation(libs.naver.map.sdk)
    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.20.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
