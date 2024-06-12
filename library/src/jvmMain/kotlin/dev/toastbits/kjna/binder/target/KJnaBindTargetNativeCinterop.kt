package dev.toastbits.kjna.binder.target

import dev.toastbits.kjna.c.CFunctionDeclaration
import dev.toastbits.kjna.c.CFunctionParameter
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

    override fun getAllFileAnnotations(): List<String> = listOf("@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)")

    override fun implementFunction(function: CFunctionDeclaration, function_header: String, header_class_name: String, context: BindingGenerator.GenerationScope): String {
        val arena_name: String = "_arena"
        var arena_used: Boolean = false

        var annotations: String = ""

        val body: String = buildString {
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
                    append("$function_param_name?.let { StableRef.create(it).asCPointer() }")

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
                    if (function.parameters.all {
                        with (context) {
                            it.type.toKotlinTypeName(
                                false,
                                createUnion = { createUnion(function.name, param_name, index, it) },
                                createStruct = { createStruct(function.name, param_name, index, it) }
                            ) == null
                        }
                    }) {
                        break
                    }
                    throw RuntimeException("void used on its own $function")
                }

                if (actual_type.type is CType.Function) {
                    if (actual_type.type.data_params == null) {
                        val uncheckedCast: String = context.importRuntimeType(RuntimeType.uncheckedCast)
                        append("$param_name?.${RuntimeType.KJnaFunctionPointer.native_function}?.$uncheckedCast()")
                    }
                    else {
                        context.importFromCoordinates("kotlinx.cinterop.staticCFunction")
                        context.importFromCoordinates("kotlinx.cinterop.asStableRef")

                        val cast_name: String? = actual_type.type.typedef_name?.let { context.importNativeName(it) }

                        append("staticCFunction")

                        if (cast_name != null) {
                            append('<')
                            with (context) {
                                var i: Int = 0
                                while (true) {
                                    if (i == actual_type.type.data_params.recv) {
                                        context.importFromCoordinates("kotlinx.cinterop.COpaquePointer")
                                        append("COpaquePointer, ")
                                    }

                                    val function_param: CFunctionParameter = actual_type.type.shape.parameters.getOrNull(i++) ?: break
                                    val type: String =
                                        function_param.type.toKotlinTypeName(
                                            false,
                                            createUnion = { createUnion(function.name, param_name, index, it) },
                                            createStruct = { createStruct(function.name, param_name, index, it) }
                                        )!!

                                    append(type)
                                    append(", ")
                                }

                                val return_type: String? =
                                    actual_type.type.shape.return_type?.toKotlinTypeName(
                                        true,
                                        createUnion = { createUnion(function.name, param_name, index, it) },
                                        createStruct = { createStruct(function.name, param_name, index, it) }
                                    )

                                append(return_type ?: "Unit")
                            }
                            append('>')
                        }

                        append(" { data -> data!!.asStableRef<${param_type.trimEnd('?')}>().get().invoke() }")

                        if (cast_name != null) {
                            append(" as $cast_name")
                        }
                    }

                    if (index + 1 != function.parameters.size) {
                        append(", ")
                    }
                    continue
                }

                append(param_name)

                append(
                    context.kToC(
                        actual_type,
                        param_type?.last() == '?',
                        function.parameters[index].type,
                        getMemScope = {
                            arena_used = true
                            return@kToC "$arena_name.${RuntimeType.KJnaMemScope.native_scope}"
                        }
                    )
                )

                if (index + 1 != function.parameters.size) {
                    append(", ")
                }
            }

            append(')')

            append(context.cToK(actual_return_type, return_type?.last() == '?', function?.return_type))
        }

        return buildString {
            append(annotations)
            append("actual ")
            append(function_header)
            appendLine(" {")

            if (arena_used) {
                val KJnaMemScope: String = context.importRuntimeType(RuntimeType.KJnaMemScope)
                appendLine("$KJnaMemScope.${RuntimeType.KJnaMemScope.confined} {")
                appendLine("    val $arena_name: ${KJnaMemScope} = this")
                appendLine(body.prependIndent("    "))
            }
            else {
                appendLine(body)
            }

            append('}')
        }
    }

    override fun implementStructConstructor(struct: CType.Struct, name: String, context: BindingGenerator.GenerationScope): String? {
        val native_type_alias: String = context.importNativeStructOrUnion(struct)
        context.importFromCoordinates("kotlinx.cinterop.ArenaBase")
        return "constructor(val $STRUCT_VALUE_PROPERTY_NAME: $native_type_alias, private val $MEM_SCOPE_PROPERTY_NAME: ArenaBase = ArenaBase())"
    }

    override fun implementStructField(name: String, index: Int, type: CValueType, type_name: String, struct: CType.Struct, struct_name: String, scope_name: String?, context: BindingGenerator.GenerationScope): String {
        return implementField(name, index, type, type_name, struct_name, false, context)
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
            append(Constants.formatKotlinFieldName(field))

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
        val native_type_alias: String = context.importNativeStructOrUnion(struct)

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
                    appendLine("    return ${struct.name}(from.${RuntimeType.KJnaTypedPointer.native_pointer}.$pointedAs())")
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
        if (struct.name == null) {
            return null
        }

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
                value_type = enm.type_name?.let { context.importNativeName(enm.name) } ?: "UInt"
            }

            context.importFromCoordinates("kotlinx.cinterop.convert")

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
                    if (value_type == "UInt") {
                        append(".convert()")
                    }
                }.prependIndent("    ")
            )

            appendLine()
            appendLine()

            val input_value_type: String = enm.type_name?.let { context.importNativeName(enm.name) } ?: value_type
            appendLine("fun ${enm.name}.Companion.$ENUM_CONVERT_FUNCTION_FROM_NATIVE(value: $input_value_type): ${enm.name} =")

            val value_accessor: String =
                if (!enm.has_explicit_value) "value.ordinal"
                else "value.toInt()"
            append("    ${enm.name}.entries.first { it.${KJnaBindTargetShared.ENUM_CLASS_VALUE_PARAM} == $value_accessor }")
        }

    companion object {
        const val STRUCT_VALUE_PROPERTY_NAME: String = "_native_value"
        const val MEM_SCOPE_PROPERTY_NAME: String = "_mem_scope"

        private const val ENUM_CONVERT_FUNCTION_TO_NATIVE: String = "toNative"
        private const val ENUM_CONVERT_FUNCTION_FROM_NATIVE: String = "fromNative"

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
            if (actual_type.type.isChar() && actual_type.pointer_depth > 0) actual_type.pointer_depth - 1
            else actual_type.pointer_depth

        val assignable: Boolean =
            actual_type.type !is CType.Union && (in_union || pointer_depth > 0 || actual_type.type !is CType.Struct)

        val field_type: String =
            if (assignable) "var"
            else "val"

        val field_name: String = Constants.formatKotlinFieldName(name)
        append("actual $field_type $field_name: $type_name")

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
                val union_name: String =
                    try {
                        with (context) {
                            actual_type.copy(pointer_depth = 0).toKotlinTypeName(false, { throw IllegalStateException() }, { throw IllegalStateException() })!!
                        }
                    }
                    catch (_: Throwable) {
                        context.getLocalClassName(scope_name ?: throw NullPointerException("$name | $index | $type | $type_name | $scope_name | $in_union"), index)
                    }

                if (pointer_depth == 0) {
                    append(" = ${type_name.trimEnd('?')}($STRUCT_VALUE_PROPERTY_NAME.$field_name)")
                }
                else {
                    val KJnaTypedPointer: String = context.importRuntimeType(RuntimeType.KJnaTypedPointer)
                    append(" = $STRUCT_VALUE_PROPERTY_NAME.$field_name?.let { $KJnaTypedPointer.ofNativeObject(it) }")
                }

                return@buildString
            }
            is CType.Enum -> {
                context.importFromCoordinates("kotlinx.cinterop.convert")
                context.importFromCoordinates(Constants.ENUM_PACKAGE_NAME + '.' + ENUM_CONVERT_FUNCTION_FROM_NATIVE)
                context.importFromCoordinates(Constants.ENUM_PACKAGE_NAME + '.' + ENUM_CONVERT_FUNCTION_TO_NATIVE)

                getter = buildString {
                    append("${type_name.trimEnd('?')}.$ENUM_CONVERT_FUNCTION_FROM_NATIVE($STRUCT_VALUE_PROPERTY_NAME.$field_name")
                    if (actual_type.type.has_explicit_value) {
                        append(".convert()")
                    }
                    append(")")
                }

                setter = buildString {
                    append("$STRUCT_VALUE_PROPERTY_NAME.$field_name = value.toNative()")
                    if (actual_type.type.has_explicit_value) {
                        append(".convert()")
                    }
                }
            }
            else -> {
                if (actual_type.type is CType.Struct && pointer_depth == 0) {
                    getter = "${type_name.trimEnd('?')}($STRUCT_VALUE_PROPERTY_NAME.$field_name)"
                    setter = "$STRUCT_VALUE_PROPERTY_NAME.$field_name = value.$STRUCT_VALUE_PROPERTY_NAME"
                }
                else {
                    getter = "$STRUCT_VALUE_PROPERTY_NAME.$field_name" + context.cToK(actual_type, type_name.last() == '?', type)
                    setter = (
                        "$STRUCT_VALUE_PROPERTY_NAME.$field_name = value" +
                        context.kToC(
                            actual_type,
                            type_name.last() == '?',
                            type,
                            auto_string = true,
                            getMemScope = {
                                TODO()
                            }
                        )
                    )
                }
            }
        }

        appendLine()
        append("    get() = ")
        append(getter)

        if (assignable) {
            appendLine()
            append("    set(value) { ")
            if (actual_type.type is CType.Function) {
                append("TODO(\"Creating function pointers without user data is not supported by Kotlin/Native\")")
            }
            else if (in_union && actual_type.type !is CType.Primitive && actual_type.pointer_depth == 0) {
                append("TODO(\"Setting non-primitive union values is not supported by Kotlin/Native\")")
            }
            else {
                if (in_union) {
                    append("if (value != null) ")
                }
                append(setter)
            }
            append(" }")
        }
    }

    private fun BindingGenerator.GenerationScope.cToK(type: CValueType?, nullable: Boolean, original_type: CValueType?): String =
        buildString {
            if (type == CValueType(CType.Primitive.CHAR, 1)) {
                importFromCoordinates("kotlinx.cinterop.toKString")
                append("?.toKString()")
            }
            else if (type?.type is CType.Function) {
                if (type.pointer_depth != 1) {
                    TODO(type.toString())
                }

                val KJnaFunctionPointer: String = importRuntimeType(RuntimeType.KJnaFunctionPointer)
                importFromCoordinates("kotlinx.cinterop.reinterpret")
                append("?.let { $KJnaFunctionPointer(it.reinterpret()) }")

                // append(".let { _function -> { ")

                // if (type.type.shape.parameters.isNotEmpty()) {
                //     val param_count: Int =
                //         type.type.shape.parameters.count { param ->
                //             param.type != CValueType(CType.Primitive.VOID, 0)
                //         }

                //     for (param in 0 until param_count) {
                //         append("p${param}")
                //         if (param + 1 != param_count) {
                //             append(", ")
                //         }
                //     }

                //     append(" ->")
                // }

                // appendLine()

                // val arena_name: String = "_function_arena"
                // var arena_used: Boolean = false

                // val body: String = buildString {
                //     importFromCoordinates("kotlinx.cinterop.invoke")
                //     append("_function!!.invoke(")

                //     val params: List<Triple<CValueType, String, CValueType>> =
                //         type.type.shape.parameters.mapNotNull { param ->
                //             val param_type: CValueType = param.type.fullyResolve(binder)

                //             val param_type_str: String =
                //                 param_type.toKotlinTypeName(
                //                     false,
                //                     createUnion = { createUnion(type.type.shape.name, null, null, it) },
                //                     createStruct = { createStruct(type.type.shape.name, null, null, it) }
                //                 ) ?: return@mapNotNull null

                //             return@mapNotNull Triple(param_type, param_type_str, param.type)
                //         }

                //     for ((index, param) in params.withIndex()) {
                //         val (param_type, param_type_str, base_type) = param

                //         append("p${index}")

                //         append(
                //             kToC(
                //                 param_type,
                //                 param_type_str.last() == '?',
                //                 base_type,
                //                 auto_string = false,
                //                 getMemScope = {
                //                     arena_used = true
                //                     return@kToC "$arena_name.${RuntimeType.KJnaMemScope.native_scope}"
                //                 }
                //             )
                //         )

                //         if (index + 1 != params.size) {
                //             append(", ")
                //         }
                //     }
                //     append(")")

                //     if (type.type.shape.return_type != null) {
                //         val actual_return_type: CValueType = type.type.shape.return_type.fullyResolve(binder)

                //         val return_type: String? =
                //             actual_return_type.toKotlinTypeName(
                //                 true,
                //                 createUnion = { createUnion(type.type.shape.name, null, null, it) },
                //                 createStruct = { createStruct(type.type.shape.name, null, null, it) }
                //             )

                //         append(cToK(actual_return_type, return_type?.last() == '?', type.type.shape.return_type))
                //     }
                // }

                // if (arena_used) {
                //     val KJnaMemScope: String = importRuntimeType(RuntimeType.KJnaMemScope)
                //     appendLine("        $KJnaMemScope.${RuntimeType.KJnaMemScope.confined} {")
                //     appendLine("            val $arena_name: ${KJnaMemScope} = this")
                //     appendLine(body.prependIndent("            "))
                //     appendLine("        }")
                // }
                // else {
                //     appendLine(body.prependIndent("        "))
                // }

                // append("    }}")
            }
            else if (type?.pointer_depth?.let { it > 0 } == true) {
                if (type.pointer_depth > 1) {
                    append("?.let { TODO(\"Nested pointers\") }")
                }
                else {
                    val pointer_constructor: String =
                        if (type.type == CType.Primitive.VOID) importRuntimeType(RuntimeType.KJnaPointer)
                        else importRuntimeType(RuntimeType.KJnaTypedPointer) + ".ofNativeObject"

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

                val converter: String
                if (!type.type.has_explicit_value) {
                    converter = ""
                }
                else {
                    importFromCoordinates("kotlinx.cinterop.convert")
                    converter = ".convert()"
                }

                append(".let { ${type.type.name}.$ENUM_CONVERT_FUNCTION_FROM_NATIVE(it$converter) }")
            }
            else if (original_type?.type is CType.Typedef && (type?.type == CType.Primitive.CHAR || type?.type == CType.Primitive.U_CHAR)) {
                val convert: String = importRuntimeType(RuntimeType.convert)
                append(".$convert()")
            }
            else if (type?.type is CType.Typedef) {
                throw IllegalArgumentException()
            }
        }

    private fun BindingGenerator.GenerationScope.kToC(type: CValueType, nullable: Boolean, original_type: CValueType?, getMemScope: () -> String, auto_string: Boolean = true): String =
        buildString {
            if (type.type == CType.Primitive.VALIST) {
                append("?.let { TODO(\"CType.Primitive.VALIST\") }")
                return@buildString
            }

            if (type.type !is CType.Enum && ((!type.type.isChar() && type.pointer_depth >= 1) || type.pointer_depth >= 2)) {
                if (nullable) {
                    append('?')
                }
                append(".pointer")
            }

            if (type.type is CType.StructOrUnion) {
                val native_type_alias: String = importNativeStructOrUnion(type.type)
                importFromCoordinates("kotlinx.cinterop.CPointer")
                if (type.pointer_depth > 1) {
                    importFromCoordinates("kotlinx.cinterop.CPointerVar")
                }
                addContainerAnnotation("@file:Suppress(\"UNCHECKED_CAST\")")

                append(" as CPointer<")

                for (i in 0 until type.pointer_depth - 1) {
                    append("CPointerVar<")
                }

                append(native_type_alias)

                for (i in 0 until type.pointer_depth) {
                    append(">")
                }

                if (nullable) {
                    append('?')
                }
            }
            else if (type.type is CType.Enum) {
                if (nullable) {
                    append('?')
                }

                if (type.pointer_depth == 0) {
                    importFromCoordinates(Constants.ENUM_PACKAGE_NAME + '.' + ENUM_CONVERT_FUNCTION_TO_NATIVE)
                    append(".$ENUM_CONVERT_FUNCTION_TO_NATIVE()")

                    if (type.type.has_explicit_value) {
                        importFromCoordinates("kotlinx.cinterop.convert")
                        append(".convert()")
                    }
                }
                else {
                    append(".castPointer()")
                }
            }
            else if (type.pointer_depth > 0) {
                if (type.type.isChar() && type.pointer_depth >= 1) {
                    if (!auto_string) {
                        importFromCoordinates("kotlinx.cinterop.cstr")

                        append("?.cstr?.getPointer(${getMemScope()})")
                    }
                    else if (type != CValueType(CType.Primitive.CHAR, 1)) {
                        importFromCoordinates("kotlinx.cinterop.CPointerVar")
                        importFromCoordinates("kotlinx.cinterop.CPointer")

                        val byte_type: String =
                            when (type.type) {
                                CType.Primitive.CHAR -> "ByteVar"
                                CType.Primitive.U_CHAR -> "UByteVar"
                                else -> throw NotImplementedError(type.toString())
                            }
                        importFromCoordinates("kotlinx.cinterop.$byte_type")

                        append(" as CPointer<")

                        for (i in 0 until type.pointer_depth - 1) {
                            append("CPointerVar<")
                        }

                        append(byte_type)

                        for (i in 0 until type.pointer_depth) {
                            append('>')
                        }

                        if (nullable) {
                            append('?')
                        }
                    }
                }
                else if (type.type is CType.Primitive) {
                    val pointed_type: String? =
                        getCPrimitivePointedType(type.type)?.also { importFromCoordinates("kotlinx.cinterop.$it") }
                        ?: "*"

                    importFromCoordinates("kotlinx.cinterop.CPointer")
                    addContainerAnnotation("@file:Suppress(\"UNCHECKED_CAST\")")

                    append(" as CPointer<")

                    val depth: Int = if (type.type.isChar()) type.pointer_depth - 2 else type.pointer_depth - 1
                    for (i in 0 until depth) {
                        importFromCoordinates("kotlinx.cinterop.CPointerVar")
                        append("CPointerVar<")
                    }
                    append(pointed_type)
                    for (i in 0 until depth + 1) {
                        append(">")
                    }

                    append('?')
                }
            }
            else if (original_type?.type is CType.Typedef && (type?.type == CType.Primitive.CHAR || type?.type == CType.Primitive.U_CHAR)) {
                importFromCoordinates("kotlinx.cinterop.convert")
                append(".code.convert()")
            }
        }
}

private fun getNativeTypeAlias(name: String): String =
    "__cinterop_" + name

private data class CinteropStructOrUnionImport(val struct_or_union: CType.StructOrUnion): BindingGenerator.Import {
    override fun getImportCoordinates(binder: KJnaBinder): String {
        val final_object: CType.StructOrUnion =
            struct_or_union.name?.let { name ->
                binder.getTypedef(name)?.type?.type as? CType.StructOrUnion
                    ?: throw ClassCastException("${binder.getTypedef(name)} is not a struct ($this)")
            } ?: struct_or_union

        // https://github.com/JetBrains/kotlin/blob/a0aa6b11e289ec15e0467bae1d990ef38e107a7c/kotlin-native/Interop/StubGenerator/src/org/jetbrains/kotlin/native/interop/gen/StubIrDriver.kt#L74
        if (final_object.anonymous_index != null) {
            return KJnaBindTargetNativeCinterop.getNativePackageName(binder.package_name) + ".anonymousStruct${final_object.anonymous_index}"
        }
        else if (final_object.isForwardDeclaration()) {
            return "cnames.structs.${final_object.name!!}"
        }
        else {
            return KJnaBindTargetNativeCinterop.getNativePackageName(binder.package_name) + '.' + final_object.name!!
        }
    }
    override fun getName(): String = getAlias()
    override fun getAlias(): String = getNativeTypeAlias(struct_or_union.anonymous_index?.let { "anonymousStruct$it" } ?: struct_or_union.name!!)
}

private fun BindingGenerator.GenerationScope.importNativeStructOrUnion(struct_or_union: CType.StructOrUnion): String {
    val import: BindingGenerator.Import = CinteropStructOrUnionImport(struct_or_union)
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
