package dev.toastbits.kjna.runtime

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.pointed

actual class KJnaPointer(val pointer: CPointer<*>) {
    actual inline fun <reified T: Any> cast(): T = TODO()
}

actual abstract class KJnaTypedPointer<T: Any>(val pointer: CPointer<*>) {
    actual abstract fun get(): T?

    actual companion object {
        inline fun <T: Any, reified I: CPointed> of(pointer: CPointer<I>, noinline construct: (I) -> T?) =
            object : KJnaTypedPointer<T>(pointer) {
                override fun get(): T? {
                    val r: I = pointer.pointedAs()
                    return construct(r)
                }
            }
    }
}

@OptIn(ExperimentalForeignApi::class)
inline fun <reified T: CPointed> CPointer<*>.pointedAs(): T =
    this.reinterpret<T>().pointed
