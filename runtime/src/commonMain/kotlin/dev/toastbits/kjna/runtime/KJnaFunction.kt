package dev.toastbits.kjna.runtime

expect class KJnaFunction {
    companion object {
        fun singleParamFunction(): KJnaFunction
        fun singleParamData(function: () -> Unit): KJnaPointer
    }
}
