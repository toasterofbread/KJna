package dev.toastbits.kjna.runtime

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.StableRef

actual class KJnaFunction(val function: CPointer<CFunction<*>>) {
    actual companion object {
        private val SINGLE_PARAM_FUNCTION: KJnaFunction =
            KJnaFunction(
                staticCFunction { data: COpaquePointer ->
                    data.asStableRef<() -> Unit>().get().invoke()
                }.reinterpret()
            )

        actual fun singleParamFunction(): KJnaFunction = SINGLE_PARAM_FUNCTION
        actual fun singleParamData(function: () -> Unit): KJnaPointer = KJnaPointer(StableRef.create(function).asCPointer())
    }
}
