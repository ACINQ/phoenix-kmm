rootProject.name = "phoenix-kmm"

include(":phoenix-shared")


val isIntelliJ = System.getProperty("idea.paths.selector").orEmpty().startsWith("IntelliJIdea")

if (!isIntelliJ) {
    // We cannot use buildSrc here for now.
    // https://github.com/gradle/gradle/issues/11090#issuecomment-734795353

    val skipAndroid: String? by settings
    val localFile = File("$rootDir/local.properties")
    val loadAndroid = when {
        skipAndroid == "true" -> false
        localFile.exists() -> {
            val localProperties = java.util.Properties()
            localFile.inputStream().use { localProperties.load(it) }
            localProperties["skip.android"] != "true"
        }
        else -> false
    }

    if (loadAndroid) {
        include(":phoenix-android")
    }
}
