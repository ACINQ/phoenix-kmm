import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    id("kotlinx-serialization")
    id("com.squareup.sqldelight")
    if (System.getProperty("includeAndroid")?.toBoolean() == true) {
        id("com.android.library")
    }
}

val includeAndroid = System.getProperty("includeAndroid")?.toBoolean() ?: false
if (includeAndroid) {
    extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
        compileSdkVersion(30)
        defaultConfig {
            minSdkVersion(24)
            targetSdkVersion(30)
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        testOptions {
            unitTests.isReturnDefaultValues = true
        }

        sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    }
}

kotlin {
    if (includeAndroid) {
        android {
            compilations.all {
                kotlinOptions.jvmTarget = "1.8"
            }
        }
    }

    ios {
        binaries {
            framework {
                baseName = "PhoenixShared"
            }
            compilations.all {
                kotlinOptions.freeCompilerArgs += "-Xoverride-konan-properties=osVersionMin.ios_x64=14.0;osVersionMin.ios_arm64=14.0"
            }
        }
    }

    sourceSets {

        val lightningkmpVersion = "snapshot"
        val coroutinesVersion = "1.4.3-native-mt"
        val serializationVersion = "1.1.0"
        val secp256k1Version = "0.5.1"
        val ktorVersion = "1.5.2"
        val kodeinMemory = "0.8.0"
        val sqldelightVersion = "1.4.4"

        val commonMain by getting {
            dependencies {
                api("fr.acinq.lightning:lightning-kmp:${Versions.lightningKmp}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.serialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:${Versions.serialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.serialization}")
                api("org.kodein.memory:kodein-memory-files:${Versions.kodeinMemory}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.datetime}")
                implementation("io.ktor:ktor-client-core:${Versions.ktor}")
                implementation("io.ktor:ktor-client-json:${Versions.ktor}")
                implementation("io.ktor:ktor-client-serialization:${Versions.ktor}")
                implementation("com.squareup.sqldelight:runtime:${Versions.sqlDelight}")
                implementation("com.squareup.sqldelight:coroutines-extensions:${Versions.sqlDelight}")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        if (includeAndroid) {
            val androidMain by getting {
                dependencies {
                    api("androidx.core:core-ktx:${Versions.Android.ktx}")
                    api("fr.acinq.secp256k1:secp256k1-kmp-jni-android:${Versions.secp256k1}")
                    implementation("io.ktor:ktor-network:${Versions.ktor}")
                    implementation("io.ktor:ktor-network-tls:${Versions.ktor}")
                    implementation("io.ktor:ktor-client-android:${Versions.ktor}")
                    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}")
                    implementation("com.squareup.sqldelight:android-driver:${Versions.sqlDelight}")
                }
            }
            val androidTest by getting {
                dependencies {
                    implementation(kotlin("test-junit"))
                    implementation("androidx.test.ext:junit:1.1.2")
                    implementation("androidx.test.espresso:espresso-core:3.3.0")
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutines}")
                    val currentOs = org.gradle.internal.os.OperatingSystem.current()
                    val target = when {
                        currentOs.isLinux -> "linux"
                        currentOs.isMacOsX -> "darwin"
                        currentOs.isWindows -> "mingw"
                        else -> error("Unsupported OS $currentOs")
                    }
                    implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-jvm-$target:${Versions.secp256k1}")
                    implementation("com.squareup.sqldelight:sqlite-driver:${Versions.sqlDelight}")
                }
            }
        }

        val iosMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-ios:${Versions.ktor}")
                implementation("com.squareup.sqldelight:native-driver:${Versions.sqlDelight}")
            }
        }

        val iosTest by getting {
            dependencies {
                implementation("com.squareup.sqldelight:native-driver:${Versions.sqlDelight}")
            }
        }

        all {
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
        }
    }
}

sqldelight {
    database("ChannelsDatabase") {
        packageName = "fr.acinq.phoenix.db"
        sourceFolders = listOf("channelsdb")
    }
    database("PaymentsDatabase") {
        packageName = "fr.acinq.phoenix.db"
        sourceFolders = listOf("paymentsdb")
    }
    database("AppDatabase") {
        packageName = "fr.acinq.phoenix.db"
        sourceFolders = listOf("appdb")
    }
}

val packForXcode by tasks.creating(Sync::class) {
    group = "build"
    val mode = (project.findProperty("XCODE_CONFIGURATION") as? String) ?: "Debug"
    val platformName = (project.findProperty("XCODE_PLATFORM_NAME") as? String) ?: "iphonesimulator"
    val targetName = when (platformName) {
        "iphonesimulator" -> "iosX64"
        "iphoneos" -> "iosArm64"
        else -> error("Unknown XCode platform $platformName")
    }
    val framework = kotlin.targets.getByName<KotlinNativeTarget>(targetName).binaries.getFramework(mode)
    inputs.property("mode", mode)
    dependsOn(framework.linkTask)
    from({ framework.outputDirectory })
    into(buildDir.resolve("xcode-frameworks"))
}
tasks.getByName("build").dependsOn(packForXcode)

afterEvaluate {
    tasks.withType<AbstractTestTask> {
        testLogging {
            events("passed", "skipped", "failed", "standard_out", "standard_error")
            showExceptions = true
            showStackTraces = true
        }
    }
}
