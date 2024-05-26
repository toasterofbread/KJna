plugins {
    kotlin("jvm")
    id("com.gradle.plugin-publish").version("1.2.1")
    signing
}

group = "dev.toastbits.kjna"
version = extra["project.version"] as String

gradlePlugin {
    website.set("https://github.com/toasterofbread/kjna")
    vcsUrl.set("https://github.com/toasterofbread/kjna.git")
    plugins {
        create("kjna") {
            id = "dev.toastbits.kjna"
            displayName = "KJna"
            description = "TOOD"
            tags.set(listOf())
            implementationClass = "dev.toastbits.kjna.plugin.KJnaPlugin"
        }
    }
}

repositories {
    mavenLocal()
    maven("https://jitpack.io")
    gradlePluginPortal()
}

dependencies {
    val project_version: String = rootProject.extra["project.version"] as String
    implementation("dev.toastbits.kjna:library:$project_version")

    implementation("de.undercouch:gradle-download-task:5.6.0")
    implementation("org.codehaus.plexus:plexus-archiver:4.9.2")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.0")
}

publishing {
    repositories {
        // publications {
        //     create<MavenPublication>("kjna") {
        //         from(components["java"])
        //     }
        // }
        mavenLocal()
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}

// signing {
//     useInMemoryPgpKeys(
//         providers.gradleProperty("signingKey").orNull,
//         providers.gradleProperty("signingPassword").orNull
//     )
// }
