import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.gradle.internal.os.OperatingSystem
import dev.toastbits.kjna.c.CType

plugins {
    kotlin("multiplatform")
    id("dev.toastbits.kjna").version("0.0.5")
}

kotlin {
    jvmToolchain(22)

    jvm()

    val native_targets: List<KotlinNativeTarget> =
        listOf(
            // Native targets are disabled in this sample because :library is JVM-only
            // linuxX64(),
            // linuxArm64(),
            // mingwX64()
        )

    applyDefaultHierarchyTemplate()

    kjna {
        generate {
            packages(native_targets) {
                add("kjna.libmpv") {
                    enabled = true
                    addHeader("mpv/client.h", "MpvClient")
                    libraries = listOf("mpv")

                    if (OperatingSystem.current().isWindows()) {
                        overrides.overrideTypedefType("size_t", CType.Primitive.LONG, pointer_depth = 0)
                    }
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
