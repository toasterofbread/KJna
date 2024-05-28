package dev.toastbits.kjna.runtime

import java.lang.foreign.Arena

actual class KJnaMemScope {
    val jvm_arena: Arena = Arena.ofConfined()

    actual fun close() {
        jvm_arena.close()
    }

    actual inline fun <reified T: Any> alloc(): KJnaTypedPointer<T> {
        require(T::class != String::class) { "String cannot be allocated directly" }

        val allocate_companion: KJnaAllocationCompanion<T> =
            KJnaAllocationCompanion.getAllocationCompanionOf<T>() ?: KJnaAllocationCompanion.ofPrimitive()

        return allocate_companion.allocate(this)
    }

    actual fun allocStringArray(values: Array<String?>): KJnaTypedPointer<String> {
        return object : KJnaTypedPointer<String>(values.memorySegment(jvm_arena)) {
            override fun get(): String { throw UnsupportedOperationException() }
            override fun set(value: String) { throw UnsupportedOperationException() }
        }
    }

    actual companion object {
        actual inline fun <T> confined(action: KJnaMemScope.() -> T): T {
            val scope: KJnaMemScope = KJnaMemScope()
            try {
                return action(scope)
            }
            finally {
                scope.close()
            }
        }
    }
}
