import org.gradle.api.Plugin
import org.gradle.api.Project
import com.android.build.gradle.LibraryExtension
import org.gradle.kotlin.dsl.get

class MaybeAndroid : Plugin<Project> {

    class Extension(private val android: LibraryExtension?) {
        val enabled get() = android != null
        fun android(block: LibraryExtension.() -> Unit) {
            android?.block()
        }
    }

    private fun Project.applyPlugin() {
        val localProperties = getLocalProperties(rootDir.absolutePath)
        extensions.add("localProperties", localProperties)

        val android = if (!localProperties.skipAndroid()) {
            apply { plugin("com.android.library") }
            extensions["android"] as LibraryExtension
        } else null
        extensions.add("maybeAndroid", Extension(android))
    }

    override fun apply(target: Project) = target.applyPlugin()
}
