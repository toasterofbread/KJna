package dev.toastbits.kjna.runtime

expect class KJnaFunctionPointer {
    companion object {
        fun getDataParam(function: () -> Unit): KJnaPointer?

        fun createDataParamFunction0(): KJnaFunctionPointer
        fun createDataParamFunction1(): KJnaFunctionPointer
        fun createDataParamFunction2(): KJnaFunctionPointer
        fun createDataParamFunction3(): KJnaFunctionPointer
    }
}
