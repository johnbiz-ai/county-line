plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
    // Real-data tests read the app's bundled assets by relative path; track them so a changed or
    // deleted asset reruns the tests instead of reusing an up-to-date result.
    inputs.files("../app/src/main/assets/counties.geojson", "../app/src/main/assets/county-facts.csv")
        .withPropertyName("bundledAssets")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    testLogging {
        events("passed", "skipped", "failed")
    }
}
