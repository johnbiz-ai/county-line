import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// Release signing config, from (in order of precedence):
//   1. env vars: COUNTYLINE_KEYSTORE, COUNTYLINE_KEYSTORE_PASSWORD, COUNTYLINE_KEY_ALIAS, COUNTYLINE_KEY_PASSWORD
//   2. keystore.properties in the repo root (git-ignored; see keystore.properties.example)
// If neither is present, the release build falls back to debug signing so CI/dev builds still work.
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(env: String, prop: String): String? =
    System.getenv(env) ?: keystoreProperties.getProperty(prop)

android {
    namespace = "net.johnbiz.countyline"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.johnbiz.countyline"
        minSdk = 26
        targetSdk = 35
        // Bump versionCode on EVERY Play upload (must strictly increase); versionName is the
        // human-facing string. See RELEASING.md.
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storePathValue = signingValue("COUNTYLINE_KEYSTORE", "storeFile")
            if (storePathValue != null) {
                storeFile = rootProject.file(storePathValue)
                storePassword = signingValue("COUNTYLINE_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("COUNTYLINE_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("COUNTYLINE_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (signingConfigs.getByName("release").storeFile != null) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("No release keystore configured; signing :app release with the debug key.")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":core"))

    constraints {
        implementation("androidx.fragment:fragment:1.8.5") {
            because(
                "play-services-base drags in fragment 1.1.0; bump it past lint's " +
                    "ActivityResult floor. The app itself uses ComponentActivity, no Fragments.",
            )
        }
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
