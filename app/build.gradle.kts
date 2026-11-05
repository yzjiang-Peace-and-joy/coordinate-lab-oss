import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localAmapProperties = Properties().apply {
    val configFile = rootProject.file("amap.local.properties")
    if (configFile.isFile) configFile.inputStream().use { load(it) }
}
val bundledAmapKey = localAmapProperties.getProperty("AMAP_ANDROID_KEY", "").trim()
require(bundledAmapKey.isEmpty() || Regex("[a-fA-F0-9]{32}").matches(bundledAmapKey)) {
    "AMAP_ANDROID_KEY in amap.local.properties must be empty or a 32-character hexadecimal Android SDK key."
}

android {
    namespace = "app.coordinate.lab"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.coordinate.lab"
        minSdk = 29
        targetSdk = 36
        versionCode = 27
        versionName = "0.2.18"
        buildConfigField("String", "AMAP_ANDROID_KEY", "\"$bundledAmapKey\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        getByName("debug") {
            val localDebugKeystore = rootProject.file(".local/debug.keystore")
            if (localDebugKeystore.isFile) storeFile = localDebugKeystore
        }
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = true
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

tasks.withType<Test>().configureEach {
    minHeapSize = "32m"
    maxHeapSize = "128m"
    maxParallelForks = 1
    jvmArgs("-XX:MaxMetaspaceSize=128m", "-XX:ReservedCodeCacheSize=48m", "-XX:ActiveProcessorCount=1", "-XX:+UseSerialGC", "-Xss512k")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.11.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.amap.api:3dmap-location-search:11.3.100_loc11.3.101_sea9.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
