import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    kotlin("android")
    id("kotlin-android")
}

val chain: String by project

val composeVersion = "1.0.0-alpha11"
val navComposeVersion = "1.0.0-alpha06"
val zxingVersion = "4.1.0"

android {
    compileSdkVersion(30)
    defaultConfig {
        applicationId = "fr.acinq.phoenix.android"
        minSdkVersion(24)
        targetSdkVersion(30)
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            resValue("string", "CHAIN", chain)
            buildConfigField("String", "CHAIN", chain)
            isDebuggable = true
        }
        release {
            resValue("string", "CHAIN", chain)
            buildConfigField("String", "CHAIN", chain)
            isMinifyEnabled = false
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        useIR = true
    }

    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = true
    }

    composeOptions {
        kotlinCompilerVersion = "1.4.21-2"
        kotlinCompilerExtensionVersion = composeVersion
    }

}

kotlin {
    target {
        compilations.all {
            kotlinOptions.freeCompilerArgs += listOf("-Xskip-metadata-version-check", "-Xinline-classes")
        }
    }
}

dependencies {
    implementation(project(":phoenix-shared"))

    implementation("com.google.android.material:material:1.3.0")
    implementation("androidx.core:core-ktx:1.3.2")
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.3.0-beta01")

    // -- jetpack compose
    implementation("androidx.compose.ui:ui:$composeVersion")
    implementation("androidx.compose.ui:ui-viewbinding:$composeVersion")
    implementation("androidx.compose.runtime:runtime-livedata:$composeVersion")
//    implementation("androidx.ui:ui-tooling:$composeVersion")
    implementation("androidx.compose.material:material:$composeVersion")
    // -- jetpack compose: navigation
    implementation("androidx.navigation:navigation-compose:$navComposeVersion")

    // -- scanner zxing
    implementation("com.journeyapps:zxing-android-embedded:$zxingVersion")
    implementation("androidx.ui:ui-tooling:1.0.0-alpha07")

    testImplementation("junit:junit:4.13.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")
}
