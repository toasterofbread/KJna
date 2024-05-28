package dev.toastbits.kjna.runtime

import kotlin.reflect.KClass

expect abstract class KJnaAllocationCompanion<T: Any> {
    val user_class: KClass<T>
    abstract fun allocate(scope: KJnaMemScope): KJnaTypedPointer<T>
    abstract fun construct(from: KJnaPointer): T
    abstract fun set(value: T, pointer: KJnaTypedPointer<T>)

    companion object {
        inline fun <reified T: Any> ofPrimitive(): KJnaAllocationCompanion<T>
    }
}
