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

class KJnaBindTargetJvmJextract(): KJnaBindTarget {
    override fun getClassModifiers(): List<String> = listOf("actual")

    override fun implementFunction(function: CFunctionDeclaration, function_header: String, header_class_name: String, context: BindingGenerator.GenerationScope): String =
        buildString {
            append("actual ")
            append(function_header)
            appendLine(" {")

            val return_type: String? =
                with (context) {
                    function.return_type.toKotlinTypeName(
                        true,
                        createUnion = { createUnion(function.name, null, null, it) },
                        createStruct = { createStruct(function.name, null, null, it) }
                    )
                }?.takeIf { it != "Unit" }

            val arena_name: String = "_arena"
            var arena_used: Boolean = false

            var function_body: String = run { buildString {
                if (return_type != null) {
                    append("return ")
                }
                append(getJvmPackageName(context.binder.package_name))
                append('.')
                append(header_class_name)
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
                        context.importFromCoordinates("java.lang.foreign.MemorySegment")
                        append("MemorySegment.NULL")

                        if (index + 1 != function.parameters.size) {
                            append(", ")
                        }
                        continue
                    }

                    val actual_type: CValueType = param.type.fullyResolve(context.binder)
                    if (actual_type.type == CType.Primitive.VALIST) {
                        return@run "TODO(\"Variadic function\")"
                    }

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
                        context.importFromCoordinates("java.lang.foreign.MemorySegment")
                        append("$param_name?.${RuntimeType.KJnaFunctionPointer.jvm_function} ?: MemorySegment.NULL")

                        if (index + 1 != function.parameters.size) {
                            append(", ")
                        }
                        continue
                    }

                    append(param_name)

                    if (actual_type.pointer_depth == 0) {
                        if (actual_type.type is CType.Enum) {
                            if (param_type.last() == '?') {
                                append('?')
                            }

                            context.importFromCoordinates(Constants.ENUM_PACKAGE_NAME + '.' + ENUM_CONVERT_FUNCTION_TO_JVM)
                            append(".$ENUM_CONVERT_FUNCTION_TO_JVM()")
                        }
                        else if (actual_type.type == CType.Primitive.U_LONG || actual_type.type == CType.Primitive.U_LONG_LONG) {
                            append(".toLong()")
                        }
                        else if (actual_type.type == CType.Primitive.U_INT) {
                            append(".toInt()")
                        }
                        else if (actual_type.type == CType.Primitive.VALIST) {
                            append("?.${RuntimeType.KJnaVarargList.jvm_data}")
                        }
                    }
                    else if (actual_type.pointer_depth == 1 && actual_type.type.isChar()) {
                        val memorySegment: String = context.importRuntimeType(RuntimeType.memorySegment)
                        arena_used = true
                        append("?.$memorySegment($arena_name)")
                    }
                    else {
                        if (param_type.last() == '?') {
                            append('?')
                        }

                        context.importFromCoordinates("java.lang.foreign.MemorySegment")
                        append(".pointer ?: MemorySegment.NULL")
                    }

                    if (index + 1 != function.parameters.size) {
                        append(", ")
                    }
                }

                append(')')

                val actual_return_type: CValueType? = function?.return_type?.fullyResolve(context.binder)

                if (actual_return_type == CValueType(CType.Primitive.CHAR, 1)) {
                    val getString: String = context.importRuntimeType(RuntimeType.getString)
                    append("?.$getString()")
                }
                else if (actual_return_type == CValueType(CType.Primitive.U_LONG, 0)) {
                    append(".toULong()")
                }
                else if (actual_return_type == CValueType(CType.Primitive.U_INT, 0)) {
                    append(".toUInt()")
                }
                else if (actual_return_type?.pointer_depth?.let { it > 0 } == true) {
                    if (actual_return_type.pointer_depth > 1) {
                        append("?.let { TODO(\"Nested pointers\") }")
                    }
                    else {
                        val pointer_depth: Int

                        if (actual_return_type.type is CType.Function) {
                            val KJnaFunctionPointer: String = context.importRuntimeType(RuntimeType.KJnaFunctionPointer)
                            append("?.let { KJnaFunctionPointer(it) }")

                            pointer_depth = actual_return_type.pointer_depth - 1
                        }
                        else {
                            pointer_depth = actual_return_type.pointer_depth
                        }

                        if (pointer_depth > 0) {
                            val pointer_constructor: String =
                                if (actual_return_type.type == CType.Primitive.VOID) context.importRuntimeType(RuntimeType.KJnaPointer)
                                else context.importRuntimeType(RuntimeType.KJnaTypedPointer) + ".ofNativeObject"

                            append("?.let { $pointer_constructor(it) }")
                        }
                    }
                }
                else if (actual_return_type?.type is CType.Enum && actual_return_type.pointer_depth == 0) {
                    context.importFromCoordinates(Constants.ENUM_PACKAGE_NAME + '.' + ENUM_CONVERT_FUNCTION_FROM_JVM)
                    append(".let { $return_type.$ENUM_CONVERT_FUNCTION_FROM_JVM(it) }")
                }
            }}

            if (arena_used) {
                context.importFromCoordinates("java.lang.foreign.Arena")
                function_body = "val $arena_name: Arena = Arena.ofAuto()\n" + function_body
            }

            appendLine(function_body.prependIndent("    "))
            append('}')
        }

    override fun implementHeaderInitialiser(all_structs: List<CType.Struct>?, context: BindingGenerator.GenerationScope): String? {
        if (all_structs == null) {
            return null
        }

        return buildString {
            val KJnaAllocationCompanion: String = context.importRuntimeType(RuntimeType.KJnaAllocationCompanion)
            context.importFromCoordinates("java.lang.foreign.Linker")
            context.importFromCoordinates("java.lang.foreign.Arena")

            appendLine("private val $LINKER_NAME: Linker by lazy { Linker.nativeLinker() }")
            appendLine("private val $FUNCTION_ARENA_NAME: Arena by lazy { Arena.ofAuto() }")
            appendLine()

            appendLine("init {")
            for (struct in all_structs.mapNotNull { it.name?.let { context.importStruct(it) } }.sorted()) {
                appendLine("    $KJnaAllocationCompanion.${RuntimeType.KJnaAllocationCompanion.registerAllocationCompanion}($struct.Companion)")
            }
            append("}")
        }
    }

    override fun implementStructConstructor(struct: CType.Struct, name: String, context: BindingGenerator.GenerationScope): String? {
        context.importFromCoordinates("java.lang.foreign.Arena")
        context.importFromCoordinates("java.lang.foreign.MemorySegment")
        return "constructor(val $STRUCT_VALUE_PROPERTY_NAME: MemorySegment, private val $MEM_SCOPE_PROPERTY_NAME: Arena = Arena.ofAuto())"
    }

    override fun implementStructField(name: String, index: Int, type: CValueType, type_name: String, struct: CType.Struct, struct_name: String, scope_name: String?, context: BindingGenerator.GenerationScope): String {
        return implementField(name, index, type, type_name, struct_name, null, false, context)
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
        context.importFromCoordinates("java.lang.foreign.Arena")
        context.importFromCoordinates("java.lang.foreign.MemorySegment")
        return "constructor(val $STRUCT_VALUE_PROPERTY_NAME: MemorySegment, private val $MEM_SCOPE_PROPERTY_NAME: Arena = Arena.ofAuto())"
    }

    override fun implementUnionField(name: String, index: Int, type: CValueType, type_name: String, union: CType.Union, union_name: String, union_field_name: String?, scope_name: String?, context: BindingGenerator.GenerationScope): String {
        return implementField(name, index, type, if (type_name.last() == '?') type_name else (type_name + '?'), scope_name + (union_field_name?.let { ".$it" } ?: ""), union_field_name, true, context)
    }

    override fun implementStructCompanionObject(struct: CType.Struct, context: BindingGenerator.GenerationScope): String? = buildString {
        val T: String = struct.name ?: return null

        append("companion object: ")
        append(context.importRuntimeType(RuntimeType.KJnaAllocationCompanion))
        appendLine("<$T>() {")

        appendLine(
            buildString {
                val KJnaTypedPointer: String = context.importRuntimeType(RuntimeType.KJnaTypedPointer)
                val KJnaPointer: String = context.importRuntimeType(RuntimeType.KJnaPointer)
                val KJnaMemScope: String = context.importRuntimeType(RuntimeType.KJnaMemScope)

                appendLine("override fun allocate(scope: $KJnaMemScope): $KJnaTypedPointer<$T> {")
                if (struct.isForwardDeclaration()) {
                    appendLine("    throw IllegalStateException(\"Forward declaration cannot be accessed directly\")")
                }
                else {
                    val jextract_type_alias: String = context.importJextractName(struct.name)
                    appendLine("    return $KJnaTypedPointer.ofNativeObject($jextract_type_alias.allocate(scope.${RuntimeType.KJnaMemScope.jvm_arena}), this)")
                }
                appendLine('}')

                appendLine("override fun construct(from: $KJnaPointer): $T {")
                if (struct.isForwardDeclaration()) {
                    appendLine("    throw IllegalStateException(\"Forward declaration cannot be accessed directly\")")
                }
                else {
                    appendLine("    return ${struct.name}(from.${RuntimeType.KJnaTypedPointer.jvm_pointer})")
                }
                appendLine('}')

                appendLine("override fun set(value: $T, pointer: $KJnaTypedPointer<$T>) {")
                if (struct.isForwardDeclaration()) {
                    appendLine("    throw IllegalStateException(\"Forward declaration cannot be accessed directly\")")
                }
                else {
                    appendLine("    pointer.pointer = value.$STRUCT_VALUE_PROPERTY_NAME")
                }
                append('}')
            }.prependIndent("    ")
        )

        append("}")
    }

    override fun implementEnumFileContent(enm: CType.Enum, context: BindingGenerator.GenerationScope): String =
        buildString {
            appendLine("fun ${enm.name}.$ENUM_CONVERT_FUNCTION_TO_JVM(): Int = this.${KJnaBindTargetShared.ENUM_CLASS_VALUE_PARAM}")
            appendLine()

            appendLine("fun ${enm.name}.Companion.$ENUM_CONVERT_FUNCTION_FROM_JVM(value: Int): ${enm.name} =")
            append("    ${enm.name}.entries.first { it.${KJnaBindTargetShared.ENUM_CLASS_VALUE_PARAM} == value }")
        }

    companion object {
        const val STRUCT_VALUE_PROPERTY_NAME: String = "_jextract_value"
        const val MEM_SCOPE_PROPERTY_NAME: String = "_mem_scope"
        private const val LINKER_NAME: String = "_linker"
        private const val FUNCTION_ARENA_NAME: String = "_function_arena"

        private const val ENUM_CONVERT_FUNCTION_TO_JVM: String = "toJvm"
        private const val ENUM_CONVERT_FUNCTION_FROM_JVM: String = "fromJvm"

        fun getJvmPackageName(package_name: String): String =
            package_name + ".jextract"
    }

    private fun implementField(
        name: String,
        index: Int,
        type: CValueType,
        type_name: String,
        scope_name: String?,
        union_field_name: String?,
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

        append("actual $field_type ${Constants.formatKotlinFieldName(name)}: $type_name")

        val name: String = Constants.formatKotlinFieldName(name)//union_field_name ?: Constants.formatKotlinFieldName(name)

        val jextract_class_name: String = context.importJextractName(scope_name!!)

        if (type_name == "String?") {
            val getString: String = context.importRuntimeType(RuntimeType.getString)
            val memorySegment: String = context.importRuntimeType(RuntimeType.memorySegment)

            appendLine()
            appendLine("    get() = $jextract_class_name.$name($STRUCT_VALUE_PROPERTY_NAME)?.$getString()")
            append("    set(value) { $jextract_class_name.$name($STRUCT_VALUE_PROPERTY_NAME, value?.$memorySegment($MEM_SCOPE_PROPERTY_NAME)) }")
            return@buildString
        }

        val getter: String
        val setter: String

        when (actual_type.type) {
            is CType.Union -> {
                val union_name: String = context.getLocalClassName(scope_name!!, index)
                appendLine(" = $union_name($jextract_class_name.$name($STRUCT_VALUE_PROPERTY_NAME))")
                return@buildString
            }
            is CType.Enum -> {
                getter = "${type_name.trimEnd('?')}.entries.first { it.${KJnaBindTargetShared.ENUM_CLASS_VALUE_PARAM} == $jextract_class_name.$name($STRUCT_VALUE_PROPERTY_NAME) }"
                setter = "$jextract_class_name.$name($STRUCT_VALUE_PROPERTY_NAME, value.${KJnaBindTargetShared.ENUM_CLASS_VALUE_PARAM})"
            }
            else -> {
                if (actual_type.type is CType.Struct && pointer_depth == 0) {
                    getter = "${type_name.trimEnd('?')}($jextract_class_name.$name($STRUCT_VALUE_PROPERTY_NAME))"
                    setter = "$jextract_class_name.$name($STRUCT_VALUE_PROPERTY_NAME, value.$STRUCT_VALUE_PROPERTY_NAME)"
                }
                else {
                    getter = buildString {
                        append("$jextract_class_name.$name($STRUCT_VALUE_PROPERTY_NAME)")

                        if (pointer_depth == 0) {
                            // if (union_field_name != null && actual_type.type is CType.Primitive) {
                            //     context.importFromCoordinates("java.lang.foreign.ValueLayout")
                            //     append(".get(")
                            //     when (actual_type.type) {
                            //         CType.Primitive.VOID -> throw IllegalStateException()
                            //         CType.Primitive.CHAR,
                            //         CType.Primitive.U_CHAR -> append("ValueLayout.JAVA_CHAR")
                            //         CType.Primitive.SHORT,
                            //         CType.Primitive.U_SHORT -> append("ValueLayout.JAVA_SHORT")
                            //         CType.Primitive.INT,
                            //         CType.Primitive.U_INT -> append("ValueLayout.JAVA_INT")
                            //         CType.Primitive.LONG,
                            //         CType.Primitive.U_LONG,
                            //         CType.Primitive.LONG_LONG,
                            //         CType.Primitive.U_LONG_LONG -> append("ValueLayout.JAVA_LONG")
                            //         CType.Primitive.FLOAT,
                            //         CType.Primitive.DOUBLE -> append("ValueLayout.JAVA_DOUBLE")
                            //         CType.Primitive.LONG_DOUBLE -> append("ValueLayout.JAVA_DOUBLE")
                            //         CType.Primitive.BOOL -> append("ValueLayout.JAVA_BOOLEAN")
                            //         CType.Primitive.VALIST -> append("TODO()")
                            //     }
                            //     append(", 0)")
                            // }

                            if (actual_type.type == CType.Primitive.U_LONG || actual_type.type == CType.Primitive.U_LONG_LONG) {
                                append(".toULong()")
                            }
                            else if (actual_type.type == CType.Primitive.U_INT) {
                                append(".toUInt()")
                            }
                            else if (actual_type.type == CType.Primitive.U_SHORT) {
                                append(".toUShort()")
                            }
                            else if (actual_type.type == CType.Primitive.CHAR || actual_type.type == CType.Primitive.U_CHAR) {
                                val convert: String = context.importRuntimeType(RuntimeType.convert)
                                append(".$convert()")
                            }

                            return@buildString
                        }

                        if (actual_type.type is CType.Function) {
                            val KJnaFunctionPointer: String = context.importRuntimeType(RuntimeType.KJnaFunctionPointer)
                            append("?.let { $KJnaFunctionPointer(it) }")
                        }
                        else {
                            if (type_name.last() == '?') {
                                append('?')
                            }

                            if (actual_type.type == CType.Primitive.VOID && actual_type.pointer_depth == 1) {
                                val KJnaPointer: String = context.importRuntimeType(RuntimeType.KJnaPointer)
                                append(".let { $KJnaPointer(it)")
                            }
                            else {
                                val KJnaTypedPointer: String = context.importRuntimeType(RuntimeType.KJnaTypedPointer)
                                append(".let { $KJnaTypedPointer.ofNativeObject(it, ")

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

                            val uncheckedCast: String = context.importRuntimeType(RuntimeType.uncheckedCast)
                            append(".$uncheckedCast()")

                            append(" }")
                        }
                    }

                    setter = buildString {
                        append("$jextract_class_name.$name($STRUCT_VALUE_PROPERTY_NAME, value")

                        if (pointer_depth == 0) {
                            // if (union_field_name != null && actual_type.type is CType.Primitive) {
                            //     val memorySegment: String = context.importRuntimeType(RuntimeType.memorySegment)
                            //     append("?.$memorySegment($MEM_SCOPE_PROPERTY_NAME)")
                            // }

                            if (actual_type.type == CType.Primitive.U_LONG || actual_type.type == CType.Primitive.U_LONG_LONG) {
                                append(".toLong()")
                            }
                            else if (actual_type.type == CType.Primitive.U_INT) {
                                append(".toInt()")
                            }
                            else if (actual_type.type == CType.Primitive.SHORT || actual_type.type == CType.Primitive.U_SHORT) {
                                append(".toShort()")
                            }
                            else if (actual_type.type == CType.Primitive.CHAR || actual_type.type == CType.Primitive.U_CHAR) {
                                val convert: String = context.importRuntimeType(RuntimeType.convert)
                                append(".$convert()")
                            }
                        }
                        else if (pointer_depth > 0) {
                            if (actual_type.type is CType.Function) {
                                append("?.${RuntimeType.KJnaFunctionPointer.jvm_function}")
                            }
                            else {
                                append("?.${RuntimeType.KJnaTypedPointer.jvm_pointer}")
                            }
                        }

                        append(')')
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
}

private fun getJextractTypeAlias(name: String): String =
    "_jextract_" + name

private fun BindingGenerator.GenerationScope.importJextractName(name: String): String {
    val jextract_type_coordinates: String = KJnaBindTargetJvmJextract.getJvmPackageName(binder.package_name) + '.' + name
    val jextract_type_alias: String = getJextractTypeAlias(name)
    importFromCoordinates(jextract_type_coordinates, jextract_type_alias)
    return jextract_type_alias
}
