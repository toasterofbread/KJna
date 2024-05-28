package dev.toastbits.kjna.runtime

class FunctionWrapper(private val func: () -> Unit) {
    fun invoke() {
        func.invoke()
    }
}
