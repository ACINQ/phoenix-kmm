val withAndroid = System.getProperty("withAndroid")!!.toBoolean()
val withIos = org.gradle.internal.os.OperatingSystem.current().isMacOsX

fun addScript(lib: String, target: String, arch: String, conf: Exec.() -> Unit): Task =
    tasks.create<Exec>("build${lib.capitalize()}${target.capitalize()}${arch.capitalize()}") {
        group = "build"
        workingDir = projectDir
        executable = projectDir.resolve("$target/$lib.sh").absolutePath
        environment("ARCH", arch)
        conf()
    }

fun PatternFilterable.includeCFiles() = include { it.file.extension == "c" || it.file.extension == "h" }

fun addLibs(target: String, arch: String, conf: Exec.() -> Unit) {
    val xz = addScript("xz", target, arch) {
        inputs.files(
            fileTree("$projectDir/libs/xz/src").includeCFiles()
        )
        outputs.files(
            file("$buildDir/$target/$arch/lib/liblzma.a"),
            file("$buildDir/$target/$arch/include/lzma.h"),
            fileTree("$buildDir/$target/$arch/include/lzma")
        )
        conf()
    }
    val openssl = addScript("openssl", target, arch) {
        inputs.files(
            fileTree("$projectDir/libs/openssl/ssl").includeCFiles(),
            fileTree("$projectDir/libs/openssl/crypto").includeCFiles(),
            fileTree("$projectDir/libs/openssl/include").includeCFiles().exclude { it.file.name.endsWith("conf.h") }
        )
        outputs.files(
            file("$buildDir/$target/$arch/lib/libssl.a"),
            file("$buildDir/$target/$arch/lib/libcrypto.a"),
            fileTree("$buildDir/$target/$arch/include/openssl")
        )
        conf()
    }
    val event = addScript("event", target, arch) {
        dependsOn(openssl)
        inputs.files(fileTree("$projectDir/libs/event").includeCFiles().exclude {
                it.file.nameWithoutExtension.endsWith(".gen")
            ||  it.file.nameWithoutExtension.endsWith("config")
            ||  it.file.nameWithoutExtension.endsWith("private")
        })
        outputs.files(
            fileTree("$buildDir/$target/$arch/lib").include("/libevent*.a"),
            fileTree("$buildDir/$target/$arch/include").include("/ev*.h"),
            fileTree("$buildDir/$target/$arch/include/event2")
        )
        conf()
    }
    val tor = addScript("tor", target, arch) {
        dependsOn(xz, openssl, event)
        inputs.files(
            fileTree("$projectDir/libs/tor/src").includeCFiles()
        )
        outputs.files(
            file("$buildDir/$target/$arch/lib/libkeccak-tiny.a"),
            fileTree("$buildDir/$target/$arch/lib").include("/lib*25519*.a"),
            fileTree("$buildDir/$target/$arch/lib").include("/libor-*.a"),
            fileTree("$buildDir/$target/$arch/lib").include("/libtor-*.a"),
            file("$buildDir/$target/$arch/include/tor_api.h")
        )
        conf()
    }
    addScript("tor_in_thread", target, arch) {
        dependsOn(tor)
        inputs.files(
            fileTree("$projectDir/tor_in_thread").includeCFiles()
        )
        outputs.files(
            file("$buildDir/$target/$arch/lib/libtor_in_thread.a"),
            file("$buildDir/$target/$arch/include/tor_in_thread.h")
        )
        conf()
    }
}

if (withAndroid) {
    val ndkVersion = "21.3.6528147"

    val localProperties = File("$rootDir/local.properties").takeIf { it.exists() }
        ?.inputStream()?.use { java.util.Properties().apply { load(it) } }
        ?: error("Android is enabled but couldn't find local.properties.")

    val sdkDir = File(localProperties["sdk.dir"] as? String
            ?: error("Android is enabled but local.properties does not contain sdk.dir."))
        .takeIf { it.exists() }
        ?: error("Local.properties sdk.dir does not exist (${localProperties["sdk.dir"]}).")

    val ndkDir = sdkDir.resolve("ndk/$ndkVersion").takeIf { it.exists() }
        ?: error("Please install Android NDK $ndkVersion.")

    addLibs("android", "arm64-v8a") { environment("NDK", ndkDir.absolutePath) }
    addLibs("android", "armeabi-v7a") { environment("NDK", ndkDir.absolutePath) }
    addLibs("android", "x86_64") { environment("NDK", ndkDir.absolutePath) }
    addLibs("android", "x86") { environment("NDK", ndkDir.absolutePath) }
}

if (withIos) {
    addLibs("ios", "arm64") {}
    addLibs("ios", "x86_64") {}
}
