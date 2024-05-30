package dev.toastbits.sample

import kjna.libmpv.MpvClient
import com.sun.jna.Library
import com.sun.jna.Native
import java.io.File

private fun configureLibraryFile() {
    val working_dir: String = System.getProperty("user.dir")
    val lib_dirs: MutableList<String> = (listOf(working_dir) + System.getProperty("java.library.path").split(";")).toMutableList()

    val os_name: String = System.getProperty("os.name")
    val lib_name: String =
        when {
            os_name == "Linux" -> {
                lib_dirs.add("/usr/lib")
                "libmpv.so"
            }
            os_name.startsWith("Win") -> "libmpv-2.dll"
            os_name == "Mac OS X" -> TODO()
            else -> throw NotImplementedError(os_name)
        }

    var lib_found: Boolean = false

    for (dir in lib_dirs) {
        val file: File = File(dir).resolve(lib_name)
        if (file.isFile) {
            kjna.libmpv.jextract.MpvClient.setLibraryByPath(file.toPath())
            lib_found = true
            break
        }
    }

    check(lib_found) { "mpv library file '$lib_name' not found in any of the following locations: $lib_dirs" }
}

actual fun createMpv(): MpvClient {
    configureLibraryFile()

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
