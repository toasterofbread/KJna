import org.jetbrains.dokka.gradle.DokkaTaskPartial

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka")
    id("com.gradle.plugin-publish").version("1.2.1")
    signing
}

group = "dev.toastbits"
version = extra["project.version"] as String

gradlePlugin {
    website.set("https://github.com/toasterofbread/KJna")
    vcsUrl.set("https://github.com/toasterofbread/KJna.git")
    plugins {
        create("kjna") {
            id = "dev.toastbits.kjna"
            displayName = "KJna"
            description = "Generates multiplatform Kotlin code for common-module access to native libraries."
            tags.set(listOf("kmp", "multiplatform", "binding", "jextract", "cinterop", "kotlin-native"))
            implementationClass = "dev.toastbits.kjna.plugin.KJnaPlugin"
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    val project_version: String = rootProject.extra["project.version"] as String
    implementation("dev.toastbits.kjna:library:$project_version")

    val json_version: String = rootProject.extra["kotlinx.serialization.json.version"] as String
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$json_version")

    implementation("de.undercouch:gradle-download-task:5.6.0")
    implementation("org.codehaus.plexus:plexus-archiver:4.9.2")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.0")
}

publishing {
    repositories {
        mavenLocal()
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<DokkaTaskPartial>().configureEach {
    moduleName.set("KJna Gradle Plugin")
}
