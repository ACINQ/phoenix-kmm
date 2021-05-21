buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.32")

//        val isIntelliJ = System.getProperty("isIntelliJ")!!.toBoolean()
//        val androidVersion = if (isIntelliJ) "4.0.1" else "7.1.0-alpha01"
//        val androidVersion = "7.1.0-alpha01"
//        classpath("com.android.tools.build:gradle:$androidVersion")
        classpath("com.android.tools.build:gradle:4.2.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.32")
        classpath("org.jetbrains.kotlin:kotlin-serialization:1.4.32")
        classpath("com.squareup.sqldelight:gradle-plugin:1.4.4")
    }
}

allprojects {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

val clean by tasks.creating(Delete::class) {
    delete(rootProject.buildDir)
}
