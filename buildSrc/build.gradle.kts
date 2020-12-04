plugins {
    `kotlin-dsl`
}

repositories {
    jcenter()
    google()
}

dependencies {
    val androidVersion = if (System.getProperty("idea.paths.selector").orEmpty().startsWith("IntelliJIdea")) "4.0.1" else "4.2.0-alpha16"
    implementation("com.android.tools.build:gradle:$androidVersion")
}

gradlePlugin {
    plugins.create("maybe-android") {
        id = "maybe-android"
        implementationClass = "MaybeAndroid"
    }
}
