import org.gradle.api.GradleException

plugins {
  alias(libs.plugins.ktlint)
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
}

val exampleSdkSource =
  providers.gradleProperty("tirtcExampleSdkSource")
    .orElse(providers.environmentVariable("TIRTC_EXAMPLE_SDK_SOURCE"))
    .orElse("published")
val exampleAvSdkVersion = providers.gradleProperty("TIRTC_ANDROID_VERSION").orElse("0.1.0")
val exampleAvSdkCoordinate =
  providers.gradleProperty("tirtcExampleAvSdkCoordinate")
    .orElse(providers.environmentVariable("TIRTC_EXAMPLE_AV_SDK_COORDINATE"))
    .orElse(exampleAvSdkVersion.map { version -> "com.tange.ai:tirtc-av:$version" })
val resolvedExampleSdkSource = exampleSdkSource.get()

if (resolvedExampleSdkSource != "published" && resolvedExampleSdkSource != "project") {
  throw GradleException(
    "invalid tirtcExampleSdkSource: $resolvedExampleSdkSource (expected published|project)",
  )
}

android {
  namespace = "com.tange.ai.tirtc.examples.server"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "com.tange.ai.tirtc.examples.server"
    minSdk = libs.versions.minSdk.get().toInt()
    targetSdk = libs.versions.targetSdk.get().toInt()
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
    }
  }

  buildFeatures {
    viewBinding = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  kotlinOptions {
    jvmTarget = "1.8"
  }
}

dependencies {
  if (resolvedExampleSdkSource == "project") {
    implementation(project(":av-sdk"))
  } else {
    implementation(exampleAvSdkCoordinate.get())
  }
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.activity.ktx)
  implementation(libs.material)
  implementation(libs.zxing.embedded)
  implementation(libs.zxing.core)
}
