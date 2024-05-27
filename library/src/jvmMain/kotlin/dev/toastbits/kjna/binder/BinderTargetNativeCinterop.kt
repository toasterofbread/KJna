package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.CFunctionDeclaration
import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CValueType
import dev.toastbits.kjna.c.resolve
import dev.toastbits.kjna.runtime.RuntimeType

class BinderTargetNativeCinterop(): KJnaBinderTarget {
    override fun getClassModifiers(): List<String> = listOf("actual")
    override fun getSourceFileExtension(): String = "native.kt"

    override fun implementKotlinFunction(function: CFunctionDeclaration, function_header: String, context: BindingGenerator.GenerationScope): String =
        buildString {
            append("actual ")
            append(function_header)
            appendLine(" {")

            val return_type: String? =
                with (context) {
                    function.return_type.toKotlinTypeName(true) { createUnion(function.name, null, it) }
                }?.takeIf { it != "Unit" }

            append("    ")
            if (return_type != null) {
                append("return ")
            }
            append(getNativePackageName(context.binder.package_name))
            append('.')
            append(function.name)
            append('(')

            val used_param_names: MutableList<String> = mutableListOf()
            for ((index, param) in function.parameters.withIndex()) {
                val actual_type: CValueType =
                    if (param.type.type is CType.TypeDef) param.type.type.resolve(context.binder.typedefs)
                    else param.type

                val param_name: String = context.getFunctionParameterName(param.name, index, used_param_names)
                used_param_names.add(param_name)

                val param_type: String? = with(context) {
                    param.type.toKotlinTypeName(false) { createUnion(function.name, index, it) }
                }
                if (param_type == null) {
                    if (function.parameters.size == 1) {
                        break
                    }

                    throw RuntimeException("void used on its own $function")
                }

                append(param_name)

                for (i in 0 until if (param.type.type == CType.Primitive.CHAR) param.type.pointer_depth - 1 else param.type.pointer_depth) {
                    if (param_type.last() == '?') {
                        append('?')
                    }
                    append(".pointer")
                }

                if (actual_type.type is CType.Struct) {
                    val native_type_alias: String = context.importNativeStruct(actual_type.type)
                    context.importFromCoordinates("kotlinx.cinterop.CPointer")
                    append(" as CPointer<$native_type_alias>")
                    if (param_type.last() == '?') {
                        append('?')
                    }
                }
                else if (actual_type.type is CType.Enum) {
                    if (param_type.last() == '?') {
                        append('?')
                    }

                    context.importFromCoordinates(Constants.ENUM_PACKAGE_NAME + '.' + Constants.NATIVE_ENUM_CONVERT_FUNCTION)
                    append(".toNative()")
                }
                else if (actual_type.type == CType.Primitive.CHAR && actual_type.pointer_depth >= 2) {
                    append(" as ")

                    context.importFromCoordinates("kotlinx.cinterop.CPointerVar")
                    context.importFromCoordinates("kotlinx.cinterop.ByteVar")
                    context.importFromCoordinates("kotlinx.cinterop.CPointer")

                    for (i in 0 until actual_type.pointer_depth - 1) {
                        append("CPointer<")
                    }
                    append("CPointerVar<ByteVar>")
                    for (i in 0 until actual_type.pointer_depth - 1) {
                        append('>')
                    }

                    if (param_type.last() == '?') {
                        append('?')
                    }
                }

                if (index + 1 != function.parameters.size) {
                    append(", ")
                }
            }

            append(')')

            if (function.return_type == CValueType(CType.Primitive.CHAR, 1)) {
                context.importFromCoordinates("kotlinx.cinterop.toKString")
                append("?.toKString()")
            }
            else if (function.return_type?.pointer_depth?.let { it > 0 } == true) {
                val typed_pointer: String = context.importRuntimeType(RuntimeType.KJnaTypedPointer)

                for (i in 0 until function.return_type.pointer_depth) {
                    if (return_type?.last() == '?') {
                        append('?')
                    }
                    // TODO
                    append(".let { $typed_pointer.ofNativeObject(it) }")
                }
            }

            appendLine()
            append('}')
        }

    private fun getNativeTypeAlias(name: String): String =
        "_native_" + name

    override fun implementKotlinStructConstructor(struct: CType.Struct, context: BindingGenerator.GenerationScope): String? {
        val native_type_alias: String = context.importNativeStruct(struct)
        context.importFromCoordinates("kotlinx.cinterop.ArenaBase")
        return "constructor(val $STRUCT_VALUE_PROPERTY_NAME: $native_type_alias, private val $MEM_SCOPE_PROPERTY_NAME: ArenaBase = ArenaBase())"
    }

    private fun BindingGenerator.GenerationScope.importNativeStruct(struct: CType.Struct): String {
        val native_type_coordinates: String =
            if (struct.definition.fields.isEmpty()) "cnames.structs.${struct.name}"
            else getNativePackageName(binder.package_name) + '.' + struct.name

        val native_type_alias: String = getNativeTypeAlias(struct.name)
        importFromCoordinates(native_type_coordinates, native_type_alias)
        return native_type_alias
    }

    private fun BindingGenerator.GenerationScope.importNativeName(name: String): String {
        val native_type_coordinates: String = getNativePackageName(binder.package_name) + '.' + name
        val native_type_alias: String = getNativeTypeAlias(name)
        importFromCoordinates(native_type_coordinates, native_type_alias)
        return native_type_alias
    }

    override fun implementKotlinStructField(name: String, index: Int, type: CValueType, type_name: String, struct: CType.Struct, context: BindingGenerator.GenerationScope): String {
        return implementField(name, index, type, type_name, struct.name, false, context)
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

    override fun implementKotlinUnionField(name: String, index: Int, type: CValueType, type_name: String, union: CType.Union, union_name: String, context: BindingGenerator.GenerationScope): String {
        return implementField(name, index, type, if (type_name.last() == '?') type_name else (type_name + '?'), union_name, true, context)
    }

    override fun getStructCompanionObject(struct: CType.Struct, context: BindingGenerator.GenerationScope): String? = buildString {
        val native_type_alias: String = getNativeTypeAlias(struct.name)
        val T: String = struct.name

        append("companion object: ")
        append(context.importRuntimeType(RuntimeType.KJnaAllocationCompanion))
        appendLine("<$T>($T::class) {")

        appendLine(
            buildString {
                val typed_pointer: String = context.importRuntimeType(RuntimeType.KJnaTypedPointer)
                val mem_scope: String = context.importRuntimeType(RuntimeType.KJnaMemScope)
                val pointedAs: String = context.importRuntimeType(RuntimeType.pointedAs)
                context.importFromCoordinates("kotlinx.cinterop.alloc")
                context.importFromCoordinates("kotlinx.cinterop.ptr")
                context.importFromCoordinates("kotlinx.cinterop.CPointer")

                appendLine("override fun allocate(scope: $mem_scope): $typed_pointer<$T> {")
                appendLine("    return $typed_pointer.ofNativeObject(scope.${RuntimeType.KJnaTypedPointer.native_scope}.alloc<$native_type_alias>().ptr, this)")
                appendLine('}')

                appendLine("override fun construct(from: $typed_pointer<$T>): $T {")
                appendLine("    return ${struct.name}(from.${RuntimeType.KJnaTypedPointer.pointer}.$pointedAs())")
                appendLine('}')

                appendLine("override fun set(value: $T, pointer: $typed_pointer<$T>) {")
                appendLine("    pointer.pointer = value.$STRUCT_VALUE_PROPERTY_NAME.ptr")
                append('}')
            }.prependIndent("    ")
        )

        append("}")
    }

    override fun getEnumFileContent(enm: CType.Enum, context: BindingGenerator.GenerationScope): String =
        buildString {
            appendLine("fun ${enm.name}.${Constants.NATIVE_ENUM_CONVERT_FUNCTION}(): UInt =")
            append(
                buildString {
                    appendLine("when (this) {")
                    for ((name, value) in enm.values) {
                        append("    ")
                        append(enm.name)
                        append('.')
                        append(name)
                        append(" -> ")
                        appendLine(context.importNativeName(name))
                    }
                    append('}')
                }.prependIndent("    ")
            )
        }

    companion object {
        const val STRUCT_VALUE_PROPERTY_NAME: String = "_native_value"
        const val MEM_SCOPE_PROPERTY_NAME: String = "_mem_scope"

        fun getNativePackageName(package_name: String): String =
            package_name + ".native"
    }

    private fun implementField(
        name: String,
        index: Int,
        type: CValueType,
        type_name: String,
        scope_name: String,
        in_union: Boolean,
        context: BindingGenerator.GenerationScope
    ): String = buildString {
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
                val union_name: String = context.getUnion(scope_name, index)
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

                    for (i in 0 until pointer_depth) {
                        if (type_name.last() == '?') {
                            append('?')
                        }

                        if (void_pointer) {
                            append(".let { $pointer_type(it)")
                        }
                        else {
                            append(".let { $pointer_type.ofNativeObject(it, ")

                            if (actual_type.type is CType.Struct) {
                                append(actual_type.type.name)
                                append(".Companion")
                            }
                            else {
                                append(context.importRuntimeType(RuntimeType.KJnaAllocationCompanion))
                                append(".ofPrimitive()")
                            }

                            append(')')
                        }
                    }

                    for (i in 0 until pointer_depth) {
                        append(" }")
                    }
                }

                setter = buildString {
                    append("$STRUCT_VALUE_PROPERTY_NAME.$name = value")
                    if (pointer_depth == 1) {
                        val internal_type: String? =
                            when (actual_type.type) {
                                is CType.Struct -> {
                                    getNativeTypeAlias(actual_type.type.name).also { alias ->
                                        context.importFromCoordinates(getNativePackageName(context.binder.package_name) + '.' + actual_type.type.name, alias)
                                    }
                                }
                                CType.Primitive.CHAR -> {
                                    if (type.pointer_depth == 2) {
                                        context.importFromCoordinates("kotlinx.cinterop.ByteVar")
                                        context.importFromCoordinates("kotlinx.cinterop.CPointerVar")
                                        "CPointerVar<ByteVar>"
                                    }
                                    else {
                                        null
                                    }
                                }
                                else -> null
                            }

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
