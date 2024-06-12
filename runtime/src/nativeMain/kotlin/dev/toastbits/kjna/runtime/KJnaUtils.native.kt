package dev.toastbits.kjna.runtime

import kotlinx.cinterop.toKString

actual object KJnaUtils {
    actual fun getEnv(name: String): String? {
        return platform.posix.getenv(name)?.toKString()
    }

    actual fun setLocale(category: Int, locale: String) {
        platform.posix.setlocale(category, locale)
    }
}
