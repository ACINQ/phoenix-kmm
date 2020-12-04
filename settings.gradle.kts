rootProject.name = "phoenix-kmm"

include(":phoenix-shared")

// We need to duplicate this code between buildSrc & here for now.
// https://github.com/gradle/gradle/issues/11090#issuecomment-734795353

val localFile = File("$rootDir/local.properties")
if (!localFile.exists()) error("Please create a $rootDir/local.properties file with either 'sdk.dir' or 'skip.android' properties")

val localProperties = java.util.Properties()
localFile.inputStream().use { localProperties.load(it) }

if (localProperties["sdk.dir"] == null && localProperties["skip.android"] != "true") {
    error("local.properties: sdk.dir == null && skip.android != true : $localProperties")
}

val skipAndroid = localProperties["skip.android"] == "true"

val isIntelliJ = System.getProperty("idea.paths.selector").orEmpty().startsWith("IntelliJIdea")

if (!skipAndroid && !isIntelliJ) {
    include(":phoenix-android")
}
