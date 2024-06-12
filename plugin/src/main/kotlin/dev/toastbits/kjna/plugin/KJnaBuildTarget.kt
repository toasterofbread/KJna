package dev.toastbits.kjna.plugin

import java.io.File

enum class KJnaBuildTarget {
    SHARED,
    JVM,
    NATIVE_ALL,

    // NATIVE_MACOS_X64,
    // NATIVE_MACOS_ARM64,
    // NATIVE_IOS_SIMULATOR_ARM64,
    // NATIVE_IOS_X64,
    NATIVE_LINUX_X64,
    NATIVE_LINUX_ARM64,
    // NATIVE_WATCHOS_SIMULATOR_ARM64,
    // NATIVE_WATCHOS_X64,
    // NATIVE_WATCHOS_ARM32,
    // NATIVE_WATCHOS_ARM64,
    // NATIVE_TVOS_SIMULATOR_ARM64,
    // NATIVE_TVOS_X64,
    // NATIVE_TVOS_ARM64,
    // NATIVE_IOS_ARM64,
    // NATIVE_ANDROID_NATIVE_ARM32,
    // NATIVE_ANDROID_NATIVE_ARM64,
    // NATIVE_ANDROID_NATIVE_X86,
    // NATIVE_ANDROID_NATIVE_X64,
    NATIVE_MINGW_X64,
    // NATIVE_WATCHOS_DEVICE_ARM64
    ;

    fun isNative(): Boolean =
        when (this) {
            SHARED,
            JVM -> false

            NATIVE_ALL,
            NATIVE_LINUX_X64,
            NATIVE_LINUX_ARM64,
            NATIVE_MINGW_X64 -> true
        }

    companion object {
        val DEFAULT_TARGETS: List<KJnaBuildTarget> = listOf(SHARED, JVM, NATIVE_ALL)
    }
}

internal fun List<KJnaBuildTarget>.normalised(): List<KJnaBuildTarget> {
    val items: List<KJnaBuildTarget> = this.distinct()
    check(items.size == this.size) { "Targets list '$this' contains duplicate item(s)" }

    var all_native: Boolean = false

    for (item in items) {
        if (item == KJnaBuildTarget.NATIVE_ALL) {
            all_native = true
        }
        else if (item.isNative() && all_native) {
            throw RuntimeException("Targets list '$this' contains NATIVE_ALL as well as a specific native target '$item'")
        }
    }

    return items
}

internal fun KJnaBuildTarget.getName(): String =
    when (this) {
        KJnaBuildTarget.SHARED -> "common"
        KJnaBuildTarget.JVM -> "jvm"
        KJnaBuildTarget.NATIVE_ALL -> "native"
        KJnaBuildTarget.NATIVE_LINUX_X64 -> "linuxX64"
        KJnaBuildTarget.NATIVE_LINUX_ARM64 -> "linuxArm64"
        KJnaBuildTarget.NATIVE_MINGW_X64 -> "mingwX64"
    }

internal fun KJnaBuildTarget.getSourceDirectory(base: File): File =
    base.resolve(getName()).resolve("kotlin")

internal fun KJnaBuildTarget.getSourceFileExtension(): String =
    when (this) {
        KJnaBuildTarget.SHARED -> "kt"
        KJnaBuildTarget.JVM -> "jvm.kt"
        KJnaBuildTarget.NATIVE_ALL,
        KJnaBuildTarget.NATIVE_LINUX_X64,
        KJnaBuildTarget.NATIVE_LINUX_ARM64,
        KJnaBuildTarget.NATIVE_MINGW_X64 -> "native.kt"
    }
