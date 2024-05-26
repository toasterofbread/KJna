package dev.toastbits.kjna.runtime

import java.lang.foreign.MemorySegment

actual class KJnaPointer(val pointer: MemorySegment) {
    actual inline fun <reified T: Any> cast(): T = TODO()
}

actual abstract class KJnaTypedPointer<T: Any>(val pointer: MemorySegment) {
    actual abstract fun get(): T?

    actual companion object
}


// fun <T: Any> NativeKJnaTypedPointer(pointer: MemorySegment, construct: (MemorySegment) -> T) =
//     object : KJnaTypedPointer<T> {
//         override fun get(): T {
//             return construct(pointer)
//         }
//     }
