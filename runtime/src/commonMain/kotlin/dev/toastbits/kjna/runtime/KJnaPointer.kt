package dev.toastbits.kjna.runtime

expect class KJnaPointer {
    inline fun <reified T: Any> cast(): T
}

expect abstract class KJnaTypedPointer<T: Any> {
    abstract fun get(): T?

    companion object
}
