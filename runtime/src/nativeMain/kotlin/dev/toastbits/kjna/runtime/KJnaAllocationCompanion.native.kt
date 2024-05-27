package dev.toastbits.kjna.runtime

import kotlin.reflect.KClass
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.pointed
import kotlinx.cinterop.set
import kotlinx.cinterop.ptr
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ShortVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.toKString
import kotlinx.cinterop.CPointer

actual abstract class KJnaAllocationCompanion<T: Any>(actual val user_class: KClass<T>) {
    init {
        KJnaMemScope.registerAllocationCompanion(this)
    }

    actual abstract fun allocate(scope: KJnaMemScope): KJnaTypedPointer<T>
    actual abstract fun construct(from: KJnaTypedPointer<T>): T
    actual abstract fun set(value: T, pointer: KJnaTypedPointer<T>)

    actual companion object {
        actual inline fun <reified T: Any> ofPrimitive(): KJnaAllocationCompanion<T> =
            object : KJnaAllocationCompanion<T>(T::class) {
                override fun allocate(scope: KJnaMemScope): KJnaTypedPointer<T> = with (scope.native_scope) {
                    val value: CPointed =
                        when (T::class) {
                            Byte::class -> alloc<ByteVar>()
                            Short::class -> alloc<ShortVar>()
                            Int::class -> alloc<IntVar>()
                            Long::class -> alloc<LongVar>()
                            UByte::class -> alloc<UByteVar>()
                            UShort::class -> alloc<UShortVar>()
                            UInt::class -> alloc<UIntVar>()
                            ULong::class -> alloc<ULongVar>()
                            Float::class -> alloc<FloatVar>()
                            Double::class -> alloc<DoubleVar>()
                            CPointer::class -> alloc<CPointerVar<*>>()
                            else -> throw RuntimeException(T::class.toString())
                        }

                    return object : KJnaTypedPointer<T>(value.ptr) {
                        override fun get(): T {
                            return construct(this)
                        }

                        override fun set(value: T) {
                            set(value, this)
                        }
                    }
                }

                @Suppress("UNCHECKED_CAST")
                override fun construct(from: KJnaTypedPointer<T>): T =
                    when (T::class) {
                        String::class -> (from.pointer as CPointer<ByteVar>).toKString() as T
                        else -> from.pointer.pointedAs()
                    }

                @Suppress("UNCHECKED_CAST")
                override fun set(value: T, pointer: KJnaTypedPointer<T>) {
                    when (value) {
                        is Byte -> (pointer.pointer as CPointer<ByteVar>).set(0, value)
                        is Short -> (pointer.pointer as CPointer<ShortVar>).set(0, value)
                        is Int -> (pointer.pointer as CPointer<IntVar>).set(0, value)
                        is Long -> (pointer.pointer as CPointer<LongVar>).set(0, value)
                        is UByte -> (pointer.pointer as CPointer<UByteVar>).set(0, value)
                        is UShort -> (pointer.pointer as CPointer<UShortVar>).set(0, value)
                        is UInt -> (pointer.pointer as CPointer<UIntVar>).set(0, value)
                        is ULong -> (pointer.pointer as CPointer<ULongVar>).set(0, value)
                        is Float -> (pointer.pointer as CPointer<FloatVar>).set(0, value)
                        is Double -> (pointer.pointer as CPointer<DoubleVar>).set(0, value)
                        is CPointer<*> -> (pointer.pointer as CPointer<CPointerVar<*>>).set(0, value)
                        else -> throw NotImplementedError(value::class.toString())
                    }
                }
            }
    }
}

@OptIn(ExperimentalForeignApi::class)
inline fun <reified T: CPointed> CPointer<*>.pointedAs(): T =
    this.reinterpret<T>().pointed
