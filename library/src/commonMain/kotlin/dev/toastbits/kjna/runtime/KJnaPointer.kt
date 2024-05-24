package dev.toastbits.kjna.runtime

class KJnaPointer<T: Any>() {
    fun get(): T? { TODO() }
    fun set(value: T?) { TODO() }
}

class KJnaVoidPointer {
    fun isNull(): Boolean { TODO() }
    fun <T> cast(): T? { TODO() }
}
