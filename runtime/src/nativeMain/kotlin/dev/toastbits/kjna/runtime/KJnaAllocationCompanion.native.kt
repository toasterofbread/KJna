package dev.toastbits.kjna.runtime

import kotlin.reflect.KClass
import kotlin.reflect.findAssociatedObject
import kotlin.reflect.AssociatedObjectKey
import kotlin.reflect.ExperimentalAssociatedObjects
import kotlin.annotation.Target
import kotlin.annotation.AnnotationTarget
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.pointed
import kotlinx.cinterop.value
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
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.toKString
import kotlinx.cinterop.CPointer

actual abstract class KJnaAllocationCompanion<T: Any> {
    actual abstract fun allocate(scope: KJnaMemScope): KJnaTypedPointer<T>
    actual abstract fun construct(from: KJnaPointer): T
    actual abstract fun set(value: T, pointer: KJnaTypedPointer<T>)

    actual companion object {
        val primitive_allocation_companions: MutableMap<KClass<*>, KJnaAllocationCompanion<*>> = mutableMapOf()

        actual inline fun <reified T: Any> ofPrimitive(): KJnaAllocationCompanion<T> {
            require(T::class != String::class) { "String is not a primitive" }

            @Suppress("UNCHECKED_CAST")
            return primitive_allocation_companions.getOrPut(T::class) {
                object : KJnaAllocationCompanion<T>() {
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
                                Boolean::class -> alloc<BooleanVar>()
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
                    override fun construct(from: KJnaPointer): T =
                        when (T::class) {
                            String::class -> (from.pointer as CPointer<ByteVar>).toKString() as T
                            Byte::class -> (from.pointer as CPointer<ByteVar>).pointed.value as T
                            Short::class -> (from.pointer as CPointer<ShortVar>).pointed.value as T
                            Int::class -> (from.pointer as CPointer<IntVar>).pointed.value as T
                            Long::class -> (from.pointer as CPointer<LongVar>).pointed.value as T
                            UByte::class -> (from.pointer as CPointer<UByteVar>).pointed.value as T
                            UShort::class -> (from.pointer as CPointer<UShortVar>).pointed.value as T
                            UInt::class -> (from.pointer as CPointer<UIntVar>).pointed.value as T
                            ULong::class -> (from.pointer as CPointer<ULongVar>).pointed.value as T
                            Float::class -> (from.pointer as CPointer<FloatVar>).pointed.value as T
                            Double::class -> (from.pointer as CPointer<DoubleVar>).pointed.value as T
                            Boolean::class -> (from.pointer as CPointer<BooleanVar>).pointed.value as T
                            else -> throw NotImplementedError(T::class.toString())
                        }

                    @Suppress("UNCHECKED_CAST")
                    override fun set(value: T, pointer: KJnaTypedPointer<T>) {
                        when (value) {
                            is Byte -> (pointer.pointer as CPointer<ByteVar>).pointed.value = value
                            is Short -> (pointer.pointer as CPointer<ShortVar>).pointed.value = value
                            is Int -> (pointer.pointer as CPointer<IntVar>).pointed.value = value
                            is Long -> (pointer.pointer as CPointer<LongVar>).pointed.value = value
                            is UByte -> (pointer.pointer as CPointer<UByteVar>).pointed.value = value
                            is UShort -> (pointer.pointer as CPointer<UShortVar>).pointed.value = value
                            is UInt -> (pointer.pointer as CPointer<UIntVar>).pointed.value = value
                            is ULong -> (pointer.pointer as CPointer<ULongVar>).pointed.value = value
                            is Float -> (pointer.pointer as CPointer<FloatVar>).pointed.value = value
                            is Double -> (pointer.pointer as CPointer<DoubleVar>).pointed.value = value
                            is Boolean -> (pointer.pointer as CPointer<BooleanVar>).pointed.value = value
                            is CPointer<*> -> (pointer.pointer as CPointer<CPointerVar<*>>).set(0, value)
                            else -> throw NotImplementedError(value::class.toString())
                        }
                    }
                }
            } as KJnaAllocationCompanion<T>
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
inline fun <reified T: CPointed> CPointer<*>.pointedAs(): T =
    this.reinterpret<T>().pointed

@OptIn(ExperimentalAssociatedObjects::class)
@Target(AnnotationTarget.CLASS)
@AssociatedObjectKey
annotation class KJnaNativeStruct(val allocation_companion: KClass<out KJnaAllocationCompanion<*>>) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        inline fun <reified T: Any> getAllocationCompanionOf(): KJnaAllocationCompanion<T>? =
            T::class.findAssociatedObject<KJnaNativeStruct>() as KJnaAllocationCompanion<T>?
    }
}
