package dev.toastbits.kjna.runtime

expect class KJnaMemScope {
    fun close()

    inline fun <reified T: Any> alloc(): KJnaTypedPointer<T>
    fun allocStringArray(values: Array<String?>): KJnaTypedPointer<String>

    companion object {
        inline fun <T> confined(action: KJnaMemScope.() -> T): T
    }
}
