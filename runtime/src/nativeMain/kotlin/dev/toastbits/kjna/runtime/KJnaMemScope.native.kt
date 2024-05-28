package dev.toastbits.kjna.runtime

import kotlinx.cinterop.MemScope as NativeMemScope
import kotlin.reflect.KClass
import kotlinx.cinterop.cstr
import kotlinx.cinterop.ptr
import kotlinx.cinterop.allocArrayOf

actual class KJnaMemScope {
    val native_scope: NativeMemScope = NativeMemScope()

    actual fun close() {
        @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
        native_scope.clearImpl()
    }

    actual inline fun <reified T: Any> alloc(): KJnaTypedPointer<T> {
        require(T::class != String::class) { "String cannot be allocated directly" }

        val allocate_companion: KJnaAllocationCompanion<T> =
            KJnaNativeStruct.getAllocationCompanionOf<T>() ?: KJnaAllocationCompanion.ofPrimitive()

        return allocate_companion.allocate(this)
    }

    actual fun allocStringArray(values: Array<String?>): KJnaTypedPointer<String> {
        return object : KJnaTypedPointer<String>(native_scope.allocArrayOf(values.map { it?.cstr?.getPointer(native_scope) })) {
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
