package dev.toastbits.kjna.runtime

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef

actual class KJnaFunctionPointer(val function: CPointer<CFunction<*>>) {
    actual companion object {
        actual fun createDataParamFunction0(): KJnaFunctionPointer = DATA_PARAM_FUNCTION_0
        actual fun createDataParamFunction1(): KJnaFunctionPointer = DATA_PARAM_FUNCTION_1
        actual fun createDataParamFunction2(): KJnaFunctionPointer = DATA_PARAM_FUNCTION_2
        actual fun createDataParamFunction3(): KJnaFunctionPointer = DATA_PARAM_FUNCTION_3

        actual fun getDataParam(function: () -> Unit): KJnaPointer? =
            KJnaPointer(StableRef.create(function).asCPointer())

        private val DATA_PARAM_FUNCTION_0: KJnaFunctionPointer =
            KJnaFunctionPointer(
                staticCFunction { data: COpaquePointer? ->
                    data!!.asStableRef<() -> Unit>().get().invoke()
                }.reinterpret()
            )
        private val DATA_PARAM_FUNCTION_1: KJnaFunctionPointer =
            KJnaFunctionPointer(
                staticCFunction { _: CPointer<*>, data: COpaquePointer? ->
                    data!!.asStableRef<() -> Unit>().get().invoke()
                }.reinterpret()
            )
        private val DATA_PARAM_FUNCTION_2: KJnaFunctionPointer =
            KJnaFunctionPointer(
                staticCFunction { _: CPointer<*>, _: CPointer<*>, data: COpaquePointer? ->
                    data!!.asStableRef<() -> Unit>().get().invoke()
                }.reinterpret()
            )
        private val DATA_PARAM_FUNCTION_3: KJnaFunctionPointer =
            KJnaFunctionPointer(
                staticCFunction { _: CPointer<*>, _: CPointer<*>, _: CPointer<*>, data: COpaquePointer? ->
                    data!!.asStableRef<() -> Unit>().get().invoke()
                }.reinterpret()
            )
    }
}
