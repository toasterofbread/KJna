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

            var function_body: String = buildString {
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
                        val FunctionWrapper: String = context.importRuntimeType(RuntimeType.FunctionWrapper)
                        context.importFromCoordinates("java.lang.foreign.FunctionDescriptor")
                        context.importFromCoordinates("java.lang.invoke.MethodHandles")
                        context.importFromCoordinates("java.lang.invoke.MethodType")

                        append("run { ")
                        append("val handle = MethodHandles.lookup().bind($FunctionWrapper(cb), \"${RuntimeType.FunctionWrapper.invoke}\", MethodType.methodType(Void.TYPE)); ")
                        append("val desc = FunctionDescriptor.ofVoid(); ")
                        append("return@run $LINKER_NAME.upcallStub(handle, desc, $FUNCTION_ARENA_NAME) ")
                        append('}')

                        if (index + 1 != function.parameters.size) {
                            append(", ")
                        }
                        continue
                    }

                    append(param_name)

                    for (i in 0 until if (param.type.type.isChar()) param.type.pointer_depth - 1 else param.type.pointer_depth) {
                        if (param_type.last() == '?') {
                            append('?')
                        }
                        append(".pointer")
                    }

                    // if (actual_type.type is CType.Struct) {
                    //     val jextract_type_alias: String = context.importJextractStruct(actual_type.type)
                    //     context.addContainerAnnotation("@file:Suppress(\"UNCHECKED_CAST\")")
                    //     append(" as CPointer<$jextract_type_alias>")
                    //     if (param_type.last() == '?') {
                    //         append('?')
                    //     }
                    // }
                    if (actual_type.type is CType.Enum) {
                        if (param_type.last() == '?') {
                            append('?')
                        }

                        append('.')
                        append(KJnaBindTargetShared.ENUM_CLASS_VALUE_PARAM)
                    }
                    else if ((actual_type.type == CType.Primitive.U_LONG || actual_type.type == CType.Primitive.U_LONG_LONG) && actual_type.pointer_depth == 0) {
                        append(".toLong()")
                    }
                    else if (actual_type.type == CType.Primitive.U_INT && actual_type.pointer_depth == 0) {
                        append(".toInt()")
                    }
                    else if (actual_type.type.isChar() && actual_type.pointer_depth == 1) {
                        val memorySegment: String = context.importRuntimeType(RuntimeType.memorySegment)
                        arena_used = true
                        append("?.$memorySegment($arena_name)")
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
                else if (actual_return_type?.pointer_depth?.let { it > 0 } == true) {
                    val pointer_constructor: String =
                        if (actual_return_type.type == CType.Primitive.VOID) context.importRuntimeType(RuntimeType.KJnaPointer)
                        else context.importRuntimeType(RuntimeType.KJnaTypedPointer) + ".ofNativeObject"

                    for (i in 0 until actual_return_type.pointer_depth) {
                        append("?.let { $pointer_constructor(it) }")
                    }
                }
            }

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

    override fun implementStructField(name: String, index: Int, type: CValueType, type_name: String, struct: CType.Struct, scope_name: String?, context: BindingGenerator.GenerationScope): String {
        return implementField(name, index, type, type_name, scope_name, null, context)
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
        context.importFromCoordinates("java.lang.foreign.Arena")
        context.importFromCoordinates("java.lang.foreign.MemorySegment")
        return "constructor(val $STRUCT_VALUE_PROPERTY_NAME: MemorySegment, private val $MEM_SCOPE_PROPERTY_NAME: Arena = Arena.ofAuto())"
    }

    override fun implementUnionField(name: String, index: Int, type: CValueType, type_name: String, union: CType.Union, union_name: String, union_field_name: String?, scope_name: String?, context: BindingGenerator.GenerationScope): String {
        return implementField(name, index, type, if (type_name.last() == '?') type_name else (type_name + '?'), scope_name, union_field_name, context)
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
                    appendLine("    return ${struct.name}(from.${RuntimeType.KJnaTypedPointer.pointer})")
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

    companion object {
        const val STRUCT_VALUE_PROPERTY_NAME: String = "_jextract_value"
        const val MEM_SCOPE_PROPERTY_NAME: String = "_mem_scope"
        private const val LINKER_NAME: String = "_linker"
        private const val FUNCTION_ARENA_NAME: String = "_function_arena"

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
        context: BindingGenerator.GenerationScope
    ): String = buildString {
        val actual_type: CValueType = type.fullyResolve(context.binder)
        val pointer_depth: Int =
            if (actual_type.type.isChar() && actual_type.pointer_depth > 0) actual_type.pointer_depth - 1
            else actual_type.pointer_depth

        val assignable: Boolean =
            actual_type.type !is CType.Union && (union_field_name != null || pointer_depth > 0 || actual_type.type !is CType.Struct)

        val field_type: String =
            if (assignable) "var"
            else "val"

        append("actual $field_type ${Constants.formatKotlinFieldName(name)}: $type_name")

        val name: String = union_field_name ?: name

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
                getter = "$type_name.entries.first { it.value == $jextract_class_name.$name($STRUCT_VALUE_PROPERTY_NAME) }"
                setter = "$jextract_class_name.$name($STRUCT_VALUE_PROPERTY_NAME, value.value)"
            }
            else -> {
                if (actual_type.type is CType.Struct && pointer_depth == 0) {
                    getter = "$type_name($jextract_class_name.$name($STRUCT_VALUE_PROPERTY_NAME))"
                    setter = "$jextract_class_name.$name($STRUCT_VALUE_PROPERTY_NAME, value.$STRUCT_VALUE_PROPERTY_NAME)"
                }
                else {
                    val void_pointer: Boolean = actual_type.type == CType.Primitive.VOID
                    val pointer_type: String =
                        if (void_pointer) context.importRuntimeType(RuntimeType.KJnaPointer)
                        else context.importRuntimeType(RuntimeType.KJnaTypedPointer)

                    getter = buildString {
                        append("$jextract_class_name.$name($STRUCT_VALUE_PROPERTY_NAME)")

                        if (pointer_depth == 0) {
                            if (union_field_name != null && actual_type.type is CType.Primitive) {
                                context.importFromCoordinates("java.lang.foreign.ValueLayout")
                                append(".get(")
                                when (actual_type.type) {
                                    CType.Primitive.VOID -> throw IllegalStateException()
                                    CType.Primitive.CHAR,
                                    CType.Primitive.U_CHAR -> append("ValueLayout.JAVA_CHAR")
                                    CType.Primitive.SHORT,
                                    CType.Primitive.U_SHORT -> append("ValueLayout.JAVA_SHORT")
                                    CType.Primitive.INT,
                                    CType.Primitive.U_INT -> append("ValueLayout.JAVA_INT")
                                    CType.Primitive.LONG,
                                    CType.Primitive.U_LONG,
                                    CType.Primitive.LONG_LONG,
                                    CType.Primitive.U_LONG_LONG -> append("ValueLayout.JAVA_LONG")
                                    CType.Primitive.FLOAT,
                                    CType.Primitive.DOUBLE -> append("ValueLayout.JAVA_DOUBLE")
                                    CType.Primitive.LONG_DOUBLE -> append("ValueLayout.JAVA_DOUBLE")
                                    CType.Primitive.BOOL -> append("ValueLayout.JAVA_BOOLEAN")
                                    CType.Primitive.VALIST -> append("TODO()")
                                }
                                append(", 0)")
                            }

                            if (actual_type.type == CType.Primitive.U_LONG || actual_type.type == CType.Primitive.U_LONG_LONG) {
                                append(".toULong()")
                            }
                            else if (actual_type.type == CType.Primitive.U_INT) {
                                append(".toUInt()")
                            }

                            return@buildString
                        }

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
                        append("$jextract_class_name.$name($STRUCT_VALUE_PROPERTY_NAME, value")

                        if (pointer_depth == 0) {
                            if (union_field_name != null && actual_type.type is CType.Primitive) {
                                val memorySegment: String = context.importRuntimeType(RuntimeType.memorySegment)
                                append("?.$memorySegment($MEM_SCOPE_PROPERTY_NAME)")
                            }

                            if (actual_type.type == CType.Primitive.U_LONG || actual_type.type == CType.Primitive.U_LONG_LONG) {
                                append(".toLong()")
                            }
                            else if (actual_type.type == CType.Primitive.U_INT) {
                                append(".toInt()")
                            }
                        }
                        else {
                            for (i in 0 until pointer_depth) {
                                append("?.pointer")
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
            if (union_field_name != null) {
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
