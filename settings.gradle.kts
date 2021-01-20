rootProject.name = "phoenix-kmm"

include(":phoenix-shared")


val isIntelliJ = System.getProperty("idea.paths.selector").orEmpty().startsWith("IntelliJIdea")

// We cannot use buildSrc here for now.
// https://github.com/gradle/gradle/issues/11090#issuecomment-734795353

val skipAndroid: String? by settings

val withAndroid = if (skipAndroid == "true") {
    false
} else {
    val localProperties = File("$rootDir/local.properties").takeIf { it.exists() }
        ?.inputStream()?.use { java.util.Properties().apply { load(it) } }
        ?: error("Please create a local.properties file (sample in local.sample.properties).")

    if (localProperties["sdk.dir"] == null && localProperties["skip.android"] != "true") {
        error("local.properties: sdk.dir == null && skip.android != true : $localProperties")
    }

    localProperties["skip.android"] != "true"
}

System.setProperty("withAndroid", withAndroid.toString())
System.setProperty("isIntelliJ", isIntelliJ.toString())

if (withAndroid && !isIntelliJ) {
    include(":phoenix-android")
}

include(":tor")