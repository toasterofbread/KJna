package dev.toastbits.kjna.runtime

expect open class KJnaPointer

expect abstract class KJnaTypedPointer<T>: KJnaPointer {
    abstract fun get(): T
    abstract fun set(value: T)

    companion object
}
