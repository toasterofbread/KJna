package dev.toastbits.kjna.runtime

import kotlin.reflect.KClass

actual abstract class KJnaAllocationCompanion<T: Any> {
    actual abstract fun allocate(scope: KJnaMemScope): KJnaTypedPointer<T>
    actual abstract fun construct(from: KJnaPointer): T
    actual abstract fun set(value: T, pointer: KJnaTypedPointer<T>)

    actual companion object {
        actual inline fun <reified T: Any> ofPrimitive(): KJnaAllocationCompanion<T> {
            TODO()
        }
    }
}
