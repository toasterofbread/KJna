package dev.toastbits.kjna.runtime

class FunctionWrapper(private val function: () -> Unit) {
    fun invoke() {
        function.invoke()
    }
}
