package dev.toastbits.kjna.runtime

import java.lang.foreign.Arena
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.foreign.MemorySegment
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

actual object KJnaUtils {
    actual fun getEnv(name: String): String? {
        return System.getenv(name)
    }

    actual fun setLocale(category: Int, locale: String) {
        val arena: Arena = Arena.ofAuto()
        val linker: Linker = Linker.nativeLinker()
        val lookup: SymbolLookup = linker.defaultLookup()

        val setlocale: MethodHandle = linker.downcallHandle(lookup.find("setlocale").get(), FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
        setlocale.invokeExact(category, locale.memorySegment(arena))
    }
}
