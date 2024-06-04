package dev.toastbits.kjna.binder.target

import dev.toastbits.kjna.c.CFunctionDeclaration
import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CValueType
import dev.toastbits.kjna.c.resolve
import dev.toastbits.kjna.c.fullyResolve
import dev.toastbits.kjna.runtime.RuntimeType
import dev.toastbits.kjna.binder.BindingGenerator
import dev.toastbits.kjna.binder.Constants
import dev.toastbits.kjna.binder.KJnaBinder
import withIndex

class KJnaBindTargetNativeCinterop(): KJnaBindTarget {
    override fun getClassModifiers(): List<String> = listOf("actual")

    override fun implementFunction(function: CFunctionDeclaration, function_header: String, header: KJnaBinder.Header, context: BindingGenerator.GenerationScope): String =
        buildString {
            append("actual ")
            append(function_header)
            appendLine(" {")

            val actual_return_type: CValueType? = function?.return_type?.fullyResolve(context.binder)

            val return_type: String? =
                with (context) {
                    actual_return_type.toKotlinTypeName(
                        true,
                        createUnion = { createUnion(function.name, null, null, it) },
                        createStruct = { createStruct(function.name, null, null, it) }
                    )
                }?.takeIf { it != "Unit" }

            append("    ")
            if (return_type != null) {
                append("return ")
            }

            val actual_parameter_types: List<CValueType> = function.parameters.map { it.type.fullyResolve(context.binder) }
            for (type in actual_parameter_types) {
                if (type.type is CType.Function && type.type.data_params == null) {
                    appendLine("TODO(\"Creating function pointers without user data is not supported in Kotlin/Native\")")
                    append('}')
                    return@buildString
                }
            }

            append(getNativePackageName(context.binder.package_name))
            append('.')
            append(function.name)
            append('(')

            val function_data_params: Map<Int, Int> = function.parameters.mapIndexedNotNull { index, param ->
                val actual_type: CValueType = param.type.fullyResolve(context.binder)

                if (actual_type.type is CType.Function && actual_type.type.data_params != null) {
                    return@mapIndexedNotNull actual_type.type.data_params.send to index
                }

                return@mapIndexedNotNull null
            }.associate { it }

            val used_param_names: MutableList<String> = mutableListOf()
            val param_names: List<String> = function.parameters.mapIndexed { index, param ->
                val param_name: String = context.getFunctionParameterName(param.name, index, used_param_names)
                used_param_names.add(param_name)
                return@mapIndexed param_name
            }

            for ((index, param) in function.parameters.withIndex()) {
                val function_param_index: Int? = function_data_params[index]
                if (function_param_index != null) {
                    context.importFromCoordinates("kotlinx.cinterop.StableRef")

                    val function_param_name: String = param_names[function_param_index]
                    append("StableRef.create($function_param_name).asCPointer()")

                    if (index + 1 != function.parameters.size) {
                        append(", ")
                    }
                    continue
                }

                val actual_type: CValueType = actual_parameter_types[index]
                val param_name: String = param_names[index]

                val param_type: String? = with(context) {
                    param.type.toKotlinTypeName(
                        false,
                        createUnion = { createUnion(function.name, param_name, index, it) },
                        createStruct = { createStruct(function.name, param_name, index, it) }
                    )
                }
                if (param_type == null) {
                    if (function.parameters.size == 1) {
                        break
                    }

                    throw RuntimeException("void used on its own $function")
                }

                if (actual_type.type is CType.Function) {
                    context.importFromCoordinates("kotlinx.cinterop.staticCFunction")
                    context.importFromCoordinates("kotlinx.cinterop.asStableRef")

                    append("staticCFunction { data -> data!!.asStableRef<$param_type>().get().invoke() }")

                    if (index + 1 != function.parameters.size) {
                        append(", ")
                    }
                    continue
                }

                append(param_name)

                if ((actual_type.type != CType.Primitive.CHAR && actual_type.pointer_depth > 1) || actual_type.pointer_depth > 2) {
                    if (param_type.last() == '?') {
                        append('?')
                    }
                    append(".pointer")
                }

                if (actual_type.type is CType.Struct) {
                    val native_type_alias: String = context.importNativeStruct(actual_type.type)
                    context.importFromCoordinates("kotlinx.cinterop.CPointer")
                    if (actual_type.pointer_depth > 1) {
                    context.importFromCoordinates("kotlinx.cinterop.CPointerVar")
                    }
                    context.addContainerAnnotation("@file:Suppress(\"UNCHECKED_CAST\")")

                    append(" as CPointer<")

                    for (i in 0 until actual_type.pointer_depth - 1) {
                        append("CPointerVar<")
                    }

                    append(native_type_alias)

                    for (i in 0 until actual_type.pointer_depth) {
                        append(">")
                    }

                    if (param_type.last() == '?') {
                        append('?')
                    }
                }
                else if (actual_type.type is CType.Enum) {
                    if (param_type.last() == '?') {
                        append('?')
                    }

                    if (actual_type.pointer_depth == 0) {
                        context.importFromCoordinates(Constants.ENUM_PACKAGE_NAME + '.' + ENUM_CONVERT_FUNCTION_TO_NATIVE)
                        append(".$ENUM_CONVERT_FUNCTION_TO_NATIVE()")
                    }
                    else {
                        append(".castPointer()")
                    }
                }
                else if (actual_type.type == CType.Primitive.CHAR) {
                    if (actual_type.pointer_depth >= 2) {
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
                }
                else if (actual_type.type is CType.Primitive && actual_type.pointer_depth > 0) {
                    val pointed_type: String? =
                        getCPrimitivePointedType(actual_type.type)?.also { context.importFromCoordinates("kotlinx.cinterop.$it") }
                        ?: "*"

                    context.importFromCoordinates("kotlinx.cinterop.CPointer")
                    context.addContainerAnnotation("@file:Suppress(\"UNCHECKED_CAST\")")

                    append("?.pointer as CPointer<$pointed_type>")

                    if (param_type.last() == '?') {
                        append('?')
                    }
                }

                if (index + 1 != function.parameters.size) {
                    append(", ")
                }
            }

            append(')')

            append(context.cToK(actual_return_type, return_type?.last() == '?'))

            appendLine()
            append('}')
        }

    override fun implementStructConstructor(struct: CType.Struct, name: String, context: BindingGenerator.GenerationScope): String? {
        val native_type_coordinates: String =
            // https://github.com/JetBrains/kotlin/blob/a0aa6b11e289ec15e0467bae1d990ef38e107a7c/kotlin-native/Interop/StubGenerator/src/org/jetbrains/kotlin/native/interop/gen/StubIrDriver.kt#L74
            if (struct.anonymous_index != null) getNativePackageName(context.binder.package_name) + ".anonymousStruct${struct.anonymous_index}"
            else getNativePackageName(context.binder.package_name) + "." + struct.name!!

        val native_type_alias: String = getNativeTypeAlias(name)
        context.importFromCoordinates(native_type_coordinates, native_type_alias)

        context.importFromCoordinates("kotlinx.cinterop.ArenaBase")
        return "constructor(val $STRUCT_VALUE_PROPERTY_NAME: $native_type_alias, private val $MEM_SCOPE_PROPERTY_NAME: ArenaBase = ArenaBase())"
    }

    override fun implementStructField(name: String, index: Int, type: CValueType, type_name: String, struct: CType.Struct, scope_name: String?, context: BindingGenerator.GenerationScope): String {
        return implementField(name, index, type, type_name, struct.name, false, context)
    }

    override fun implementStructToStringMethod(struct: CType.Struct, context: BindingGenerator.GenerationScope): String = buildString {
        appendLine("override fun toString(): String = ")
        append("    \"")
        append(struct.name)
        append('(')

        for ((index, field, _) in struct.definition?.fields?.withIndex() ?: emptyList()) {
            append(field)
            append('=')
            append('$')
            append(field)

            if (index + 1 != struct.definition?.fields?.size) {
                append(", ")
            }
        }
        append(")\"")
    }

    override fun implementUnionConstructor(union: CType.Union, name: String, context: BindingGenerator.GenerationScope): String? {
        val native_type_coordinates: String =
            // https://github.com/JetBrains/kotlin/blob/a0aa6b11e289ec15e0467bae1d990ef38e107a7c/kotlin-native/Interop/StubGenerator/src/org/jetbrains/kotlin/native/interop/gen/StubIrDriver.kt#L74
            if (union.anonymous_index != null) getNativePackageName(context.binder.package_name) + ".anonymousStruct${union.anonymous_index}"
            else getNativePackageName(context.binder.package_name) + "." + union.name!!

        val native_type_alias: String = getNativeTypeAlias(name)
        context.importFromCoordinates(native_type_coordinates, native_type_alias)

        context.importFromCoordinates("kotlinx.cinterop.ArenaBase")
        return "constructor(val $STRUCT_VALUE_PROPERTY_NAME: $native_type_alias, private val $MEM_SCOPE_PROPERTY_NAME: ArenaBase = ArenaBase())"
    }

    override fun implementUnionField(name: String, index: Int, type: CValueType, type_name: String, union: CType.Union, union_name: String, union_field_name: String?, scope_name: String?, context: BindingGenerator.GenerationScope): String {
        return implementField(name, index, type, if (type_name.last() == '?') type_name else (type_name + '?'), union_name, true, context)
    }

    override fun implementStructCompanionObject(struct: CType.Struct, context: BindingGenerator.GenerationScope): String? = buildString {
        val T: String = struct.name ?: return null
        val native_type_alias: String = context.importNativeStruct(struct)

        append("companion object: ")
        append(context.importRuntimeType(RuntimeType.KJnaAllocationCompanion))
        appendLine("<$T>() {")

        appendLine(
            buildString {
                val KJnaTypedPointer: String = context.importRuntimeType(RuntimeType.KJnaTypedPointer)
                val KJnaPointer: String = context.importRuntimeType(RuntimeType.KJnaPointer)
                val KJnaMemScope: String = context.importRuntimeType(RuntimeType.KJnaMemScope)
                val pointedAs: String = context.importRuntimeType(RuntimeType.pointedAs)
                context.importFromCoordinates("kotlinx.cinterop.alloc")
                context.importFromCoordinates("kotlinx.cinterop.ptr")
                context.importFromCoordinates("kotlinx.cinterop.CPointer")

                appendLine("override fun allocate(scope: $KJnaMemScope): $KJnaTypedPointer<$T> {")
                if (struct.isForwardDeclaration()) {
                    appendLine("    throw IllegalStateException(\"Forward declaration cannot be accessed directly\")")
                }
                else {
                    appendLine("    return $KJnaTypedPointer.ofNativeObject(scope.${RuntimeType.KJnaMemScope.native_scope}.alloc<$native_type_alias>().ptr, this)")
                }
                appendLine('}')

                appendLine("override fun construct(from: $KJnaPointer): $T {")
                if (struct.isForwardDeclaration()) {
                    appendLine("    throw IllegalStateException(\"Forward declaration cannot be accessed directly\")")
                }
                else {
                    appendLine("    return ${struct.name}(from.${RuntimeType.KJnaTypedPointer.pointer}.$pointedAs())")
                }
                appendLine('}')

                appendLine("override fun set(value: $T, pointer: $KJnaTypedPointer<$T>) {")
                if (struct.isForwardDeclaration()) {
                    appendLine("    throw IllegalStateException(\"Forward declaration cannot be accessed directly\")")
                }
                else {
                    appendLine("    pointer.pointer = value.$STRUCT_VALUE_PROPERTY_NAME.ptr")
                }
                append('}')
            }.prependIndent("    ")
        )

        append("}")
    }

    override fun implementStructAnnotation(struct: CType.Struct, context: BindingGenerator.GenerationScope): String? {
        val KJnaNativeStruct: String = context.importRuntimeType(RuntimeType.KJnaNativeStruct)
        return "@$KJnaNativeStruct(${struct.name}.Companion::class)"
    }

    override fun implementEnumFileContent(enm: CType.Enum, context: BindingGenerator.GenerationScope): String =
        buildString {
            val value_type: String

            // Kotlin/Native seems to generate a Kotlin enum class only for C enums with no explicitly ('=') set values
            // This is just an educated guess though
            if (!enm.has_explicit_value) {
                value_type = context.importNativeName(enm.name)
            }
            else {
                value_type = "UInt"
            }

            appendLine("fun ${enm.name}.$ENUM_CONVERT_FUNCTION_TO_NATIVE(): $value_type =")
            append(
                buildString {
                    appendLine("when (this) {")
                    for ((name, value) in enm.values) {
                        append("    ${enm.name}.$name -> ")

                        if (!enm.has_explicit_value) {
                            append("$value_type.$name")
                        }
                        else {
                            append(context.importNativeName(name))
                        }
                        appendLine()
                    }
                    append('}')
                }.prependIndent("    ")
            )

            appendLine()
            appendLine()

            appendLine("fun ${enm.name}.Companion.$ENUM_CONVERT_FUNCTION_FROM_NATIVE(value: $value_type): ${enm.name} =")

            val value_accessor: String =
                if (!enm.has_explicit_value) "value.ordinal"
                else "value.toInt()"
            append("    ${enm.name}.entries.first { it.value == $value_accessor }")
        }

    companion object {
        const val STRUCT_VALUE_PROPERTY_NAME: String = "_native_value"
        const val MEM_SCOPE_PROPERTY_NAME: String = "_mem_scope"

        const val ENUM_CONVERT_FUNCTION_TO_NATIVE: String = "toNative"
        const val ENUM_CONVERT_FUNCTION_FROM_NATIVE: String = "fromNative"

        fun getNativePackageName(package_name: String): String =
            package_name + ".cinterop"
    }

    private fun implementField(
        name: String,
        index: Int,
        type: CValueType,
        type_name: String,
        scope_name: String?,
        in_union: Boolean,
        context: BindingGenerator.GenerationScope
    ): String = buildString {
        val actual_type: CValueType = type.fullyResolve(context.binder)
        val pointer_depth: Int =
            if (actual_type.type == CType.Primitive.CHAR && actual_type.pointer_depth > 0) actual_type.pointer_depth - 1
            else actual_type.pointer_depth

        val assignable: Boolean =
            actual_type.type !is CType.Union && (in_union || pointer_depth > 0 || actual_type.type !is CType.Struct)

        val field_type: String =
            if (assignable) "var"
            else "val"

        append("actual $field_type ${Constants.formatKotlinFieldName(name)}: $type_name")

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
                val union_name: String = context.getLocalClassName(scope_name!!, index)
                appendLine(" = $union_name($STRUCT_VALUE_PROPERTY_NAME.$name)")
                return@buildString
            }
            is CType.Enum -> {
                context.importFromCoordinates("kotlinx.cinterop.convert")

                getter = "$type_name.entries.first { it.value == $STRUCT_VALUE_PROPERTY_NAME.$name.convert<Int>() }"
                setter = "$STRUCT_VALUE_PROPERTY_NAME.$name = value.value.convert()"
            }
            else -> {
                if (actual_type.type is CType.Struct && pointer_depth == 0) {
                    getter = "$type_name($STRUCT_VALUE_PROPERTY_NAME.$name)"
                    setter = "$STRUCT_VALUE_PROPERTY_NAME.$name = value.$STRUCT_VALUE_PROPERTY_NAME"
                }
                else {
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
                                    append(context.importStruct(actual_type.type.name!!))
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
                                        context.importNativeStruct(actual_type.type)
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
                                context.addContainerAnnotation("@file:Suppress(\"UNCHECKED_CAST\")")
                                append(" as CPointer<$internal_type>")
                            }
                        }

                        if (pointer_depth > 1) {
                            TODO("$actual_type $type_name")
                        }
                    }
                }
            }
        }

        appendLine()
        append("    get() = ")
        append(getter)

        if (assignable) {
            appendLine()
            append("    set(value) { ")
            if (in_union) {
                append("if (value != null) ")
            }
            append(setter)
            append(" }")
        }
    }

    private fun BindingGenerator.GenerationScope.cToK(type: CValueType?, nullable: Boolean): String =
        buildString {
            if (type == CValueType(CType.Primitive.CHAR, 1)) {
                importFromCoordinates("kotlinx.cinterop.toKString")
                append("?.toKString()")
            }
            else if (type?.type is CType.Function) {
                if (type.pointer_depth != 1) {
                    TODO(type.toString())
                }

                append(".let{{ ")

                if (type.type.shape.parameters.isNotEmpty()) {
                    for (param in 0 until type.type.shape.parameters.size) {
                        append("p${param}")
                        if (param + 1 != type.type.shape.parameters.size) {
                            append(", ")
                        }
                    }

                    append(" -> ")
                }

                importFromCoordinates("kotlinx.cinterop.invoke")
                append("it?.invoke(")

                for (param in 0 until type.type.shape.parameters.size) {
                    append("p${param}")
                    if (param + 1 != type.type.shape.parameters.size) {
                        append(", ")
                    }
                }
                append(")")

                if (type.type.shape.return_type != null) {
                    val actual_return_type: CValueType = type.type.shape.return_type.fullyResolve(binder)

                    val return_type: String? =
                        actual_return_type.toKotlinTypeName(
                            true,
                            createUnion = { createUnion(type.type.shape.name, null, null, it) },
                            createStruct = { createStruct(type.type.shape.name, null, null, it) }
                        )

                    append(cToK(actual_return_type, return_type?.last() == '?'))
                }
                append(" }}")
            }
            else if (type?.pointer_depth?.let { it > 0 } == true) {
                val pointer_constructor: String =
                    if (type.type == CType.Primitive.VOID) importRuntimeType(RuntimeType.KJnaPointer)
                    else importRuntimeType(RuntimeType.KJnaTypedPointer) + ".ofNativeObject"

                for (i in 0 until type.pointer_depth) {
                    append("?.let { $pointer_constructor(it) }")
                }
            }
            else if ((type?.type as? CType.Primitive)?.isInteger() == true) {
                importFromCoordinates("kotlinx.cinterop.convert")
                if (nullable) {
                    append('?')
                }
                append(".convert()")
            }
            else if (type?.type is CType.Enum) {
                importFromCoordinates(Constants.ENUM_PACKAGE_NAME + '.' + ENUM_CONVERT_FUNCTION_FROM_NATIVE)
                append(".let { ${type.type.name}.$ENUM_CONVERT_FUNCTION_FROM_NATIVE(it) }")
            }
            else if (type?.type is CType.Typedef) {
                throw IllegalArgumentException()
            }
        }

    private fun CValueType.kToC(): String =
        buildString {

        }
}

private fun getNativeTypeAlias(name: String): String =
    "__cinterop_" + name

private data class CinteropStructImport(val struct_name: String): BindingGenerator.Import {
    override fun getImportCoordinates(binder: KJnaBinder): String {
        val struct: CType.Struct = (binder.typedefs[struct_name]?.type?.type as? CType.Struct) ?: throw ClassCastException("${binder.typedefs[struct_name]} is not a struct ($this)")
        if (struct.isForwardDeclaration()) {
            return "cnames.structs.$struct_name"
        }
        else {
            return KJnaBindTargetNativeCinterop.getNativePackageName(binder.package_name) + '.' + struct_name
        }
    }
    override fun getName(): String = getAlias()
    override fun getAlias(): String = getNativeTypeAlias(struct_name)
}

private fun BindingGenerator.GenerationScope.importNativeStruct(struct: CType.Struct): String {
    val import: BindingGenerator.Import = CinteropStructImport(struct.name!!)
    addImport(import)
    return import.getName()
}

private fun BindingGenerator.GenerationScope.importNativeName(name: String): String {
    val native_type_coordinates: String = KJnaBindTargetNativeCinterop.getNativePackageName(binder.package_name) + '.' + name
    val native_type_alias: String = getNativeTypeAlias(name)
    importFromCoordinates(native_type_coordinates, native_type_alias)
    return native_type_alias
}

private fun getCPrimitivePointedType(type: CType.Primitive): String? =
    when (type) {
        CType.Primitive.VOID -> null
        CType.Primitive.CHAR -> "ByteVar"
        CType.Primitive.U_CHAR -> "UByteVar"
        CType.Primitive.SHORT -> "ShortVar"
        CType.Primitive.U_SHORT -> "UShortVar"
        CType.Primitive.INT -> "IntVar"
        CType.Primitive.U_INT -> "UIntVar"
        CType.Primitive.LONG,
        CType.Primitive.LONG_LONG -> "LongVar"
        CType.Primitive.U_LONG,
        CType.Primitive.U_LONG_LONG -> "ULongVar"
        CType.Primitive.FLOAT -> "FloatVar"
        CType.Primitive.DOUBLE,
        CType.Primitive.LONG_DOUBLE -> "DoubleVar"
        CType.Primitive.BOOL -> "BooleanVar"
        CType.Primitive.VALIST -> TODO()
    }
