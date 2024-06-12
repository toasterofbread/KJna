package dev.toastbits.kjna.runtime

import java.lang.foreign.Linker
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.foreign.FunctionDescriptor
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.reflect.KClass

actual class KJnaFunctionPointer(val function: MemorySegment) {
    actual companion object {
        val linker: Linker by lazy { Linker.nativeLinker() }
        private val arena: Arena by lazy { Arena.ofAuto() }

        actual fun getDataParam(function: () -> Unit): KJnaPointer? {
            val handle: MethodHandle =
                MethodHandles.lookup().bind(
                    KotlinFunction0(function),
                    "invoke",
                    MethodType.methodType(Void.TYPE)
                )
            val descriptor: FunctionDescriptor = FunctionDescriptor.ofVoid()
            return KJnaPointer(linker.upcallStub(handle, descriptor, arena))
        }

        actual fun createDataParamFunction0(): KJnaFunctionPointer = createDataParamFunction(0)
        actual fun createDataParamFunction1(): KJnaFunctionPointer = createDataParamFunction(1)
        actual fun createDataParamFunction2(): KJnaFunctionPointer = createDataParamFunction(2)
        actual fun createDataParamFunction3(): KJnaFunctionPointer = createDataParamFunction(3)

        fun bindObjectMethod(obj: Any, method_name: String, return_type: KClass<*>, param_types: List<KClass<*>> = emptyList()): KJnaFunctionPointer {
            val handle: MethodHandle =
                MethodHandles.lookup().bind(
                    obj,
                    method_name,
                    MethodType.methodType(return_type.java, param_types.map { it.java })
                )

            val param_layout_types: Array<ValueLayout> =
                Array(param_types.size) { param_types[it].getLayout() }

            val descriptor: FunctionDescriptor =
                if (return_type == Unit::class) FunctionDescriptor.ofVoid(*param_layout_types)
                else FunctionDescriptor.of(return_type.getLayout(), *param_layout_types)

            return KJnaFunctionPointer(linker.upcallStub(handle, descriptor, arena))
        }

        private fun createDataParamFunction(param_index: Int): KJnaFunctionPointer {
            val handle: MethodHandle =
                MethodHandles.lookup().bind(
                    DataParamFunction(),
                    DataParamFunction.getMethodName(param_index),
                    getDataParamFunctionMethodType(param_index)
                )
            return KJnaFunctionPointer(linker.upcallStub(handle, getDataParamFunctionDescriptor(param_index), arena))
        }

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

            fun invoke2(p0: MemorySegment, p1: MemorySegment, p2: MemorySegment) {
                invokeFunction(p2, 2)
            }

            fun invoke3(p0: MemorySegment, p1: MemorySegment, p2: MemorySegment, p3: MemorySegment) {
                invokeFunction(p3, 3)
            }

            companion object {
                private const val MAX_PARAM_INDEX: Int = 3

                fun getMethodName(param_index: Int): String {
                    if (param_index > DataParamFunction.MAX_PARAM_INDEX) {
                        throw NotImplementedError("Current max implemented param index is ${DataParamFunction.MAX_PARAM_INDEX}")
                    }

                    return "invoke$param_index"
                }
            }
        }

        internal class KotlinFunction0(val function: () -> Unit) {
            fun invoke(): Unit = function()
        }

        private fun KClass<*>.getLayout(): ValueLayout =
            when (this) {
                MemorySegment::class -> ValueLayout.ADDRESS
                Boolean::class -> ValueLayout.JAVA_BOOLEAN
                Byte::class -> ValueLayout.JAVA_BYTE
                Char::class -> ValueLayout.JAVA_CHAR
                Double::class -> ValueLayout.JAVA_DOUBLE
                Float::class -> ValueLayout.JAVA_FLOAT
                Int::class -> ValueLayout.JAVA_INT
                Long::class -> ValueLayout.JAVA_LONG
                Short::class -> ValueLayout.JAVA_SHORT
                else -> throw NotImplementedError(this.toString())
            }
    }
}
