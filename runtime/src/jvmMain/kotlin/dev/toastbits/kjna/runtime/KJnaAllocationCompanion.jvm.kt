package dev.toastbits.kjna.runtime

import kotlin.reflect.KClass
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

actual abstract class KJnaAllocationCompanion<T: Any> {
    actual abstract fun allocate(scope: KJnaMemScope): KJnaTypedPointer<T>
    actual abstract fun construct(from: KJnaPointer): T
    actual abstract fun set(value: T, pointer: KJnaTypedPointer<T>)

    actual companion object {
        val registered_allocation_companions: MutableMap<KClass<*>, KJnaAllocationCompanion<*>> = mutableMapOf()

        inline fun <reified T: Any> registerAllocationCompanion(allocation_companion: KJnaAllocationCompanion<T>) {
            registered_allocation_companions[T::class] = allocation_companion
        }

        @Suppress("UNCHECKED_CAST")
        inline fun <reified T: Any> getAllocationCompanionOf(): KJnaAllocationCompanion<T>? =
            registered_allocation_companions[T::class] as KJnaAllocationCompanion<T>?

        val primitive_allocation_companions: MutableMap<KClass<*>, KJnaAllocationCompanion<*>> = mutableMapOf()

        actual inline fun <reified T: Any> ofPrimitive(): KJnaAllocationCompanion<T> {
            require(T::class != String::class) { "String is not a primitive" }

            @Suppress("UNCHECKED_CAST")
            return primitive_allocation_companions.getOrPut(T::class) {
                object : KJnaAllocationCompanion<T>() {
                    override fun allocate(scope: KJnaMemScope): KJnaTypedPointer<T> {
                        val layout: ValueLayout =
                            when (T::class) {
                                UByte::class,
                                Byte::class -> ValueLayout.JAVA_BYTE
                                UShort::class,
                                Short::class -> ValueLayout.JAVA_SHORT
                                UInt::class,
                                Int::class -> ValueLayout.JAVA_INT
                                ULong::class,
                                Long::class -> ValueLayout.JAVA_LONG
                                Float::class -> ValueLayout.JAVA_FLOAT
                                Double::class -> ValueLayout.JAVA_DOUBLE
                                Boolean::class -> ValueLayout.JAVA_INT
                                MemorySegment::class -> ValueLayout.ADDRESS
                                else -> throw RuntimeException(T::class.toString())
                            }

                        return object : KJnaTypedPointer<T>(scope.jvm_arena.allocate(layout.byteSize(), 1L)) {
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
                            String::class -> from.pointer.getString() as T
                            Byte::class -> from.pointer.get(ValueLayout.JAVA_BYTE, 0L) as T
                            Short::class -> from.pointer.get(ValueLayout.JAVA_SHORT, 0L) as T
                            Int::class -> from.pointer.get(ValueLayout.JAVA_INT, 0L) as T
                            Long::class -> from.pointer.get(ValueLayout.JAVA_LONG, 0L) as T
                            UByte::class -> from.pointer.get(ValueLayout.JAVA_BYTE, 0L).toUByte() as T
                            UShort::class -> from.pointer.get(ValueLayout.JAVA_SHORT, 0L).toUShort() as T
                            UInt::class -> from.pointer.get(ValueLayout.JAVA_INT, 0L).toUInt() as T
                            ULong::class -> from.pointer.get(ValueLayout.JAVA_LONG, 0L).toULong() as T
                            Float::class -> from.pointer.get(ValueLayout.JAVA_FLOAT, 0L) as T
                            Double::class -> from.pointer.get(ValueLayout.JAVA_DOUBLE, 0L) as T
                            Boolean::class -> from.pointer.get(ValueLayout.JAVA_INT, 0L).let { it == 1 } as T
                            else -> throw NotImplementedError(T::class.toString())
                        }

                    @Suppress("UNCHECKED_CAST")
                    override fun set(value: T, pointer: KJnaTypedPointer<T>) {
                        when (value) {
                            is Byte -> pointer.pointer.set(ValueLayout.JAVA_BYTE, 0L, value)
                            is Short -> pointer.pointer.set(ValueLayout.JAVA_SHORT, 0L, value)
                            is Int -> pointer.pointer.set(ValueLayout.JAVA_INT, 0L, value)
                            is Long -> pointer.pointer.set(ValueLayout.JAVA_LONG, 0L, value)
                            is UByte -> pointer.pointer.set(ValueLayout.JAVA_BYTE, 0L, value.toByte())
                            is UShort -> pointer.pointer.set(ValueLayout.JAVA_SHORT, 0L, value.toShort())
                            is UInt -> pointer.pointer.set(ValueLayout.JAVA_INT, 0L, value.toInt())
                            is ULong -> pointer.pointer.set(ValueLayout.JAVA_LONG, 0L, value.toLong())
                            is Float -> pointer.pointer.set(ValueLayout.JAVA_FLOAT, 0L, value)
                            is Double -> pointer.pointer.set(ValueLayout.JAVA_DOUBLE, 0L, value)
                            is Boolean -> pointer.pointer.set(ValueLayout.JAVA_INT, 0L, if (value) 1 else 0)
                            is MemorySegment -> pointer.pointer.set(ValueLayout.ADDRESS, 0L, value)
                            else -> throw NotImplementedError(value::class.toString())
                        }
                    }
                }
            } as KJnaAllocationCompanion<T>
        }
    }
}
