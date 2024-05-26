package dev.toastbits.kjna.runtime

data class RuntimeType(
    val name: String,
    val coordinates: String
) {
    companion object {
        val KJnaPointer = RuntimeType("KJnaPointer", "dev.toastbits.kjna.runtime.KJnaPointer")
        val KJnaTypedPointer = RuntimeType("KJnaTypedPointer", "dev.toastbits.kjna.runtime.KJnaTypedPointer")
    }
}
