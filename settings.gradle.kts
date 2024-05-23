pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        val kotlin_version: String = extra["kotlin.version"] as String
        kotlin("multiplatform").version(kotlin_version)

        val dokka_version: String = extra["dokka.version"] as String
        id("org.jetbrains.dokka").version(dokka_version)

        id("com.vanniktech.maven.publish").version("0.28.0")

        val antlr_kotlin_version: String = extra["antlrKotlin.version"] as String
        id("com.strumenta.antlr-kotlin").version(antlr_kotlin_version)
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "kjna"
include(":library")
include(":sample")
