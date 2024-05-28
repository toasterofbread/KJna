import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    id("dev.toastbits.kjna")
}

kotlin {
    jvmToolchain(22)

    jvm()

    val native_targets: List<KotlinNativeTarget> =
        listOf(
            linuxX64(),
            linuxArm64(),
            mingwX64()
        )

    applyDefaultHierarchyTemplate()

    kjna {
        generate {
            packages(native_targets) {
                add("kjna.libmpv") {
                    enabled = true
                    addHeader("mpv/client.h", "MpvClient")
                    libraries = listOf("mpv")
                }
            }
        }
    }

    sourceSets {
        all {
            languageSettings.enableLanguageFeature("ExpectActualClasses")
        }

        val commonMain by getting {
            dependencies {
                implementation(project(":library"))
                implementation(project(":runtime"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("net.java.dev.jna:jna:5.14.0")
            }
        }
    }
}
