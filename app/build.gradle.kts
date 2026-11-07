plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
val packagedAbi = providers.gradleProperty("scanifyAbi").orNull
require(packagedAbi == null || packagedAbi in setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")) { "Unsupported scanifyAbi" }
android {
 namespace = "com.scanify.nativeapp"
 compileSdk = 36
 defaultConfig { applicationId = "com.scanify.nativeapp"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "0.1.0"; testInstrumentationRunner = "com.scanify.nativeapp.StoreSmokeRunner" }
 packagedAbi?.let { defaultConfig.ndk.abiFilters.add(it) }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.10.01"))
 implementation("androidx.activity:activity-compose:1.11.0")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.foundation:foundation")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("androidx.core:core-ktx:1.17.0")
 implementation("androidx.exifinterface:exifinterface:1.4.1")
 implementation("androidx.camera:camera-camera2:1.5.3")
 implementation("androidx.camera:camera-lifecycle:1.5.3")
 implementation("androidx.camera:camera-view:1.5.3")
 implementation("org.opencv:opencv:4.12.0")
 debugImplementation("androidx.compose.ui:ui-tooling")
}
