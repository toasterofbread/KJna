package dev.toastbits.kjna.runtime

import kotlinx.cinterop.MemScope as NativeMemScope
import kotlin.reflect.KClass

actual class KJnaMemScope {
    val native_scope: NativeMemScope = NativeMemScope()

    actual fun close() {
        @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
        native_scope.clearImpl()
    }

    actual inline fun <reified T: Any> alloc(): KJnaTypedPointer<T> {
        allocateWithCompanion(T::class)?.also {
            return it
        }

        when (T::class) {
            else -> throw TODO(T::class.toString())
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: Any> allocateWithCompanion(user_class: KClass<T>): KJnaTypedPointer<T>? =
        getAllocationCompanion(user_class)?.let { (it as KJnaAllocationCompanion<T>).allocate(this) }

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

        private val allocation_companions: MutableMap<KClass<*>, KJnaAllocationCompanion<*>> = mutableMapOf()

        fun <T: Any> getAllocationCompanion(user_class: KClass<T>): KJnaAllocationCompanion<T>? =
            allocation_companions[user_class] as KJnaAllocationCompanion<T>?

        internal fun registerAllocationCompanion(obj: KJnaAllocationCompanion<*>) {
            check(allocation_companions.none { it.key == obj.user_class || it.value == obj })
            allocation_companions[obj.user_class] = obj
        }
    }
}
