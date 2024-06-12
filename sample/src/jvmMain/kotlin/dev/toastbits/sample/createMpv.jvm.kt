package dev.toastbits.sample

import kjna.libmpv.MpvClient
import com.sun.jna.Library
import com.sun.jna.Native
import java.io.File

actual fun createMpv(): MpvClient {
    try {
        setlocale(
            1, // LC_NUMERIC
            "C"
        )
    }
    catch (_: Throwable) {}

    return MpvClient()
}

private interface PosixInterface: Library {
    fun setlocale(category: Int, locale: String): String
    fun pthread_mutexattr_setrobust()
}

private val posix: PosixInterface by lazy { Native.load("c", PosixInterface::class.java) }
private fun setlocale(category: Int, locale: String) {
    posix.setlocale(category, locale)
}
