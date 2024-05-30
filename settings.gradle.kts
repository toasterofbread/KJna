rootProject.name = "KJna"

include(":plugin")
include(":library")
include(":runtime")
include(":sample")

pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        val kotlin_version: String = extra["kotlin.version"] as String
        kotlin("jvm").version(kotlin_version)
        kotlin("plugin.serialization").version(kotlin_version)

        id("com.vanniktech.maven.publish").version("0.28.0")

        val antlr_kotlin_version: String = extra["antlrKotlin.version"] as String
        id("com.strumenta.antlr-kotlin").version(antlr_kotlin_version)

        val dokka_version: String = extra["dokka.version"] as String
        id("org.jetbrains.dokka").version(dokka_version)

        // Plugin included for use in Sample
        val kjna_version: String = extra["project.version"] as String
        id("dev.toastbits.kjna").version(kjna_version)
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
