plugins {
    alias(libs.plugins.android.application)
}

val rawTagName = System.getenv("GITHUB_REF_NAME")?.takeIf { it.startsWith("v") }
    ?: providers.exec {
        commandLine("git", "describe", "--tags", "--abbrev=0", "--match", "v*")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().takeIf { it.startsWith("v") }

val computedVersionName = rawTagName?.removePrefix("v") ?: "1.0.8"
val versionParts = computedVersionName.split(".").mapNotNull { it.toIntOrNull() }
val computedVersionCode = if (versionParts.size >= 3) {
    versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]
} else {
    10
}

android {
    namespace = "com.example.appsapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.safiullin.batterify"
        minSdk = 23
        targetSdk = 36
        versionCode = computedVersionCode
        versionName = computedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            optimization {
                enable = true // Enables code and resource optimizations.
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}