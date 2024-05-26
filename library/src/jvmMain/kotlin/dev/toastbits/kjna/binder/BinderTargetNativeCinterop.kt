package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.CFunctionDeclaration
import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CValueType
import dev.toastbits.kjna.c.resolve
import dev.toastbits.kjna.runtime.RuntimeType

class BinderTargetNativeCinterop(): KJnaBinderTarget {
    override fun getClassModifiers(): List<String> = listOf("actual")
    override fun getSourceFileExtension(): String = "native.kt"

    override fun implementKotlinFunction(function: CFunctionDeclaration, function_header: String, context: BindingGenerator.GenerationScope): String {
        return "actual $function_header { TODO() }"
    }

    private fun getNativeTypeAlias(name: String): String =
        "_native_" + name

    override fun implementKotlinStructConstructor(struct: CType.Struct, context: BindingGenerator.GenerationScope): String? {
        val native_type_coordinates: String =
            if (struct.definition.fields.isEmpty()) "cnames.structs.${struct.name}"
            else getNativePackageName(context.binder.package_name) + '.' + struct.name

        val native_type_alias: String = getNativeTypeAlias(struct.name)
        context.importFromCoordinates(native_type_coordinates, native_type_alias)

        context.importFromCoordinates("kotlinx.cinterop.ArenaBase")

        return "constructor(val $STRUCT_VALUE_PROPERTY_NAME: $native_type_alias, private val $MEM_SCOPE_PROPERTY_NAME: ArenaBase = ArenaBase())"
    }

    override fun implementKotlinStructField(name: String, type: CValueType, type_name: String, struct: CType.Struct, context: BindingGenerator.GenerationScope): String {
        return implementField(name, type, type_name, false, context)
    }

    override fun implementKotlinUnionConstructor(union: CType.Union, name: String, context: BindingGenerator.GenerationScope): String? {
        val native_type_coordinates: String =
            // https://github.com/JetBrains/kotlin/blob/a0aa6b11e289ec15e0467bae1d990ef38e107a7c/kotlin-native/Interop/StubGenerator/src/org/jetbrains/kotlin/native/interop/gen/StubIrDriver.kt#L74
            if (union.anonymous_index != null) getNativePackageName(context.binder.package_name) + ".anonymousStruct${union.anonymous_index}"
            else TODO(union.toString())

        val native_type_alias: String = getNativeTypeAlias(name)
        context.importFromCoordinates(native_type_coordinates, native_type_alias)

        context.importFromCoordinates("kotlinx.cinterop.ArenaBase")

        return "constructor(val $STRUCT_VALUE_PROPERTY_NAME: $native_type_alias, private val $MEM_SCOPE_PROPERTY_NAME: ArenaBase = ArenaBase())"
    }

    override fun implementKotlinUnionField(name: String, type: CValueType, type_name: String, union: CType.Union, context: BindingGenerator.GenerationScope): String {
        return implementField(name, type, if (type_name.last() == '?') type_name else (type_name + '?'), true, context)
    }

    companion object {
        const val STRUCT_VALUE_PROPERTY_NAME: String = "_native_value"
        const val MEM_SCOPE_PROPERTY_NAME: String = "_mem_scope"

        fun getNativePackageName(package_name: String): String =
            package_name + ".native"
    }

    private fun implementField(name: String, type: CValueType, type_name: String, in_union: Boolean, context: BindingGenerator.GenerationScope): String {
        return buildString {
            var pointer_depth: Int = type.pointer_depth
            val actual_type: CValueType =
                if (type.type is CType.TypeDef) type.type.resolve(context.binder.typedefs).also { pointer_depth += it.pointer_depth }
                else type

            if (actual_type.type == CType.Primitive.CHAR && pointer_depth > 0) {
                pointer_depth--
            }

            val field_type: String =
                if (actual_type.type is CType.Union) "val"
                else "var"
            append("actual $field_type $name: $type_name")

            if (type_name == "String?") {
                context.importFromCoordinates("kotlinx.cinterop.toKString")
                context.importFromCoordinates("kotlinx.cinterop.cstr")

                appendLine()
                appendLine("    get() = $STRUCT_VALUE_PROPERTY_NAME.$name?.toKString()")
                append("    set(value) { $STRUCT_VALUE_PROPERTY_NAME.$name = value?.cstr?.getPointer($MEM_SCOPE_PROPERTY_NAME) }")
                return@buildString
            }

            val getter: String
            val setter: String

            when (actual_type.type) {
                is CType.Union -> {
                    val union_name: String = getUnionTypeName(name)
                    appendLine(" = $union_name($STRUCT_VALUE_PROPERTY_NAME.$name)")
                    return@buildString
                }
                is CType.Enum -> {
                    context.importFromCoordinates("kotlinx.cinterop.convert")

                    getter = "$type_name.entries.first { it.value == $STRUCT_VALUE_PROPERTY_NAME.$name.convert<Int>() }"
                    setter = "$STRUCT_VALUE_PROPERTY_NAME.$name = value.value.convert()"
                }
                else -> {
                    val void_pointer: Boolean = actual_type.type == CType.Primitive.VOID
                    val pointer_type: String =
                        if (void_pointer) context.importRuntimeType(RuntimeType.KJnaPointer)
                        else context.importRuntimeType(RuntimeType.KJnaTypedPointer)

                    getter = buildString {
                        append("$STRUCT_VALUE_PROPERTY_NAME.$name")

                        if (pointer_depth == 0) {
                            return@buildString
                        }

                        for (i in 0 .. pointer_depth) {
                            if (i == pointer_depth) {
                                if (void_pointer) {
                                    // append("$pointer_type(it)")
                                }
                                else if (actual_type.type is CType.Struct) {
                                    append("${actual_type.type.name}(it)")
                                }
                                else if (actual_type.type == CType.Primitive.CHAR) {
                                    context.importFromCoordinates("kotlinx.cinterop.toKString")
                                    context.importFromCoordinates("kotlinx.cinterop.value")
                                    append("it.value?.toKString()")
                                }
                                else {
                                    // append("it ")
                                }
                            }
                            else {
                                if (type_name.last() == '?') {
                                    append('?')
                                }

                                if (void_pointer) {
                                    append(".let { $pointer_type(it)")
                                }
                                else {
                                    // ?.let { KJnaTypedPointer.of(it) { mpv_node(it) } }
                                    // ?.let { KJnaTypedPointer.of(it) { it.value?.let { KJnaTypedPointer.of<Char, kotlinx.cinterop.ByteVar>(it) { it.value.toChar() } } } }

                                    append(".let { $pointer_type.of(it) { ")
                                }
                            }
                        }

                        for (i in 0 .. if (void_pointer) pointer_depth - 1 else pointer_depth) {
                            append(" }")
                        }
                    }

                    setter = buildString {
                        val internal_type: String? =
                            when (actual_type.type) {
                                is CType.Struct -> {
                                    getNativeTypeAlias(actual_type.type.name).also { alias ->
                                        context.importFromCoordinates(getNativePackageName(context.binder.package_name) + '.' + actual_type.type.name, alias)
                                    }
                                }
                                else -> null
                            }

                        append("$STRUCT_VALUE_PROPERTY_NAME.$name = value")
                        if (pointer_depth == 1) {
                            append("?.pointer")
                            if (internal_type != null) {
                                context.importFromCoordinates("kotlinx.cinterop.CPointer")
                                append(" as CPointer<$internal_type>")
                            }
                        }

                        if (pointer_depth > 1) {
                            TODO("$actual_type $type_name")
                        }
                    }
                }
            }

            appendLine()
            append("    get() = ")
            appendLine(getter)

            append("    set(value) { ")
            if (in_union) {
                append("if (value != null) ")
            }
            append(setter)
            append(" }")
        }
    }
}
