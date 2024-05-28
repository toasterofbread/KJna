package dev.toastbits.kjna.runtime

import kotlinx.cinterop.CPointer

actual open class KJnaPointer(var pointer: CPointer<*>) {
    actual inline fun <reified T: Any> cast(): T {
        try {
            val allocation_companion: KJnaAllocationCompanion<T> =
                KJnaNativeStruct.getAllocationCompanionOf<T>() ?: KJnaAllocationCompanion.ofPrimitive()

            return allocation_companion.construct(this)
        }
        catch (e: Throwable) {
            throw RuntimeException("Casting $this to ${T::class} failed", e)
        }
    }
}

actual abstract class KJnaTypedPointer<T>(pointer: CPointer<*>): KJnaPointer(pointer) {
    actual abstract fun get(): T
    actual abstract fun set(value: T)

    actual companion object {
        inline fun <reified T: Any> ofNativeObject(
            pointer: CPointer<*>,
            allocation_companion: KJnaAllocationCompanion<T> =
                KJnaNativeStruct.getAllocationCompanionOf<T>() ?: throw RuntimeException(T::class.toString())
        ) = object : KJnaTypedPointer<T>(pointer) {
                override fun get(): T {
                    return allocation_companion.construct(this)
                }

                override fun set(value: T) {
                    allocation_companion.set(value, this)
                }
            }
    }
}
