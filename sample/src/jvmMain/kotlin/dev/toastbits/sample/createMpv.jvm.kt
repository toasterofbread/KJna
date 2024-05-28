package dev.toastbits.sample

import kjna.libmpv.MpvClient
import com.sun.jna.Library
import com.sun.jna.Native

private interface PosixInterface: Library {
    fun setlocale(category: Int, locale: String): String
    fun pthread_mutexattr_setrobust()
}

private val posix: PosixInterface = Native.load("c", PosixInterface::class.java)
private fun setlocale(category: Int, locale: String) {
    posix.setlocale(category, locale)
}

actual fun createMpv(): MpvClient {
    setlocale(
        1, // LC_NUMERIC
        "C"
    )

    return MpvClient()
}
