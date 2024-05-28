import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.gradle.DokkaTaskPartial

plugins {
    kotlin("jvm").apply(false)
    id("dev.toastbits.kjna").apply(false)
    id("com.vanniktech.maven.publish").apply(false)

    id("org.jetbrains.dokka")
}

buildscript {
    dependencies {
        val dokka_version: String = rootProject.extra["dokka.version"] as String
        classpath("org.jetbrains.dokka:dokka-base:$dokka_version")
    }
}

val footer_message: String = "© 2024 Talo Halton"

tasks.dokkaHtmlMultiModule {
    moduleName.set("KJna")

    pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
        footerMessage = footer_message
        customStyleSheets = listOf(file("dokka-style.css"))
    }
}

allprojects {
    tasks.withType<DokkaTaskPartial>().configureEach {
        pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
            footerMessage = footer_message
            customStyleSheets = listOf(file("dokka-style.css"))
        }
    }
}
