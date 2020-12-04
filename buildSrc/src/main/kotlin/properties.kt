import java.io.File
import java.util.*

fun getLocalProperties(rootDir: String): Properties {
    val localFile = File("$rootDir/local.properties")
    if (!localFile.exists()) error("Please create a $rootDir/local.properties file with either 'sdk.dir' or 'skip.android' properties")

    val properties = java.util.Properties()
    localFile.inputStream().use { properties.load(it) }

    if (properties["sdk.dir"] == null && properties["skip.android"] != "true") {
        error("local.properties: sdk.dir == null && skip.android != true : ${properties}")
    }

    return properties
}
