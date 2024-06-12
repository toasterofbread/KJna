package dev.toastbits.kjna.runtime

@Suppress("UNCHECKED_CAST")
fun <T: Any> Any.uncheckedCast(): T = this as T
