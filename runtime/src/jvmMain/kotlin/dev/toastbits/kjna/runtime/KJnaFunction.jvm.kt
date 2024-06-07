package dev.toastbits.kjna.runtime

actual class KJnaFunction {
    actual companion object {
        actual fun singleParamFunction(): KJnaFunction = TODO()
        actual fun singleParamData(function: () -> Unit): KJnaPointer = TODO()
    }
}
