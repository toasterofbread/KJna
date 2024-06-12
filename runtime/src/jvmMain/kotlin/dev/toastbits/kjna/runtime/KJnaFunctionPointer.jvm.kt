package dev.toastbits.kjna.runtime

import java.lang.foreign.Linker
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.foreign.FunctionDescriptor
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

actual class KJnaFunctionPointer(val function: MemorySegment) {
    actual companion object {
        private val linker: Linker by lazy { Linker.nativeLinker() }
        private val arena: Arena by lazy { Arena.ofAuto() }

        actual fun getDataParam(function: () -> Unit): KJnaPointer? {
            val handle: MethodHandle =
                MethodHandles.lookup().bind(
                    KotlinFunction0(function),
                    "invokeUnit",
                    MethodType.methodType(Void.TYPE)
                )
            val descriptor: FunctionDescriptor = FunctionDescriptor.ofVoid()
            return KJnaPointer(linker.upcallStub(handle, descriptor, arena))
        }

        actual fun createDataParamFunction0(): KJnaFunctionPointer = createDataParamFunction(0)
        actual fun createDataParamFunction1(): KJnaFunctionPointer = createDataParamFunction(1)
        actual fun createDataParamFunction2(): KJnaFunctionPointer = createDataParamFunction(2)
        actual fun createDataParamFunction3(): KJnaFunctionPointer = createDataParamFunction(3)

        private fun createDataParamFunction(param_index: Int): KJnaFunctionPointer {
            val handle: MethodHandle =
                MethodHandles.lookup().bind(
                    DataParamFunction(),
                    "invoke$param_index",
                    getDataParamFunctionMethodType(param_index)
                )
            return KJnaFunctionPointer(linker.upcallStub(handle, getDataParamFunctionDescriptor(param_index), arena))
        }

        @Suppress("NON_PUBLIC_CALL_FROM_PUBLIC_INLINE")
        inline fun <reified T: Any> ofKotlinFunction0(noinline function: () -> T): KJnaFunctionPointer {
            val method_name: String
            val method_type: MethodType
            val descriptor: FunctionDescriptor

            when (T::class) {
                Int::class -> {
                    method_name = "invokeInt"
                    method_type = MethodType.methodType(Int::class.java)
                    descriptor = FunctionDescriptor.of(ValueLayout.JAVA_INT)
                }
                else -> throw NotImplementedError(T::class.toString())
            }

            val handle: MethodHandle =
                MethodHandles.lookup().bind(
                    // Type is always erased with lambda or generic class
                    // Using hardcoded methods for each type is the only way I could find that works
                    KotlinFunction0(function),
                    method_name,
                    method_type
                )
            return KJnaFunctionPointer.ofMethodHandle(handle, descriptor)
        }

        @Suppress("NON_PUBLIC_CALL_FROM_PUBLIC_INLINE")
        inline fun <reified T: Any, reified P0: Any> ofKotlinFunction1(noinline function: (P0) -> T): KJnaFunctionPointer {
            val method_name: String
            val method_type: MethodType
            val descriptor: FunctionDescriptor
            val obj: Any

            when (T::class to P0::class) {
                Int::class to Int::class -> {
                    method_name = "invokeInt"
                    method_type = MethodType.methodType(Int::class.java, Int::class.java)
                    descriptor = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
                    obj = KotlinFunction1Int(function as (Int) -> Any)
                }
                else -> throw NotImplementedError("${T::class}, ${P0::class}")
            }

            val handle: MethodHandle =
                MethodHandles.lookup().bind(
                    obj,
                    method_name,
                    method_type
                )
            return KJnaFunctionPointer.ofMethodHandle(handle, descriptor)
        }

        fun ofMethodHandle(handle: MethodHandle, descriptor: FunctionDescriptor) =
            KJnaFunctionPointer(linker.upcallStub(handle, descriptor, arena))

        private fun getDataParamFunctionDescriptor(param_index: Int): FunctionDescriptor =
            FunctionDescriptor.ofVoid(*Array(param_index + 1) { ValueLayout.ADDRESS })

        private fun getDataParamFunctionMethodType(param_index: Int): MethodType =
            MethodType.methodType(Void.TYPE, Array(param_index + 1) { MemorySegment::class.java })

        private class DataParamFunction {
            private fun invokeFunction(function: MemorySegment, param_index: Int) {
                KJnaFunctionPointer.linker.downcallHandle(function, FunctionDescriptor.ofVoid()).invoke()
            }

            fun invoke0(p0: MemorySegment) {
                invokeFunction(p0, 0)
            }

            fun invoke1(p0: MemorySegment, p1: MemorySegment) {
                invokeFunction(p1, 1)
            }
        }
    }
}

internal class KotlinFunction0(val function: () -> Any) {
    fun invokeUnit(): Unit = function() as Unit
    fun invokeInt(): Int = function() as Int
}

internal class KotlinFunction1Int(val function: (Int) -> Any) {
    fun invokeInt(p0: Int): Int = function(p0) as Int
}
