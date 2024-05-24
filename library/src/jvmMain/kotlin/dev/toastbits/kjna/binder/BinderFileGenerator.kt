package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.*
import dev.toastbits.kjna.runtime.KJnaPointer
import dev.toastbits.kjna.runtime.KJnaVoidPointer
import kotlin.reflect.KClass

class BinderFileGenerator(
    val binder: KJnaBinder,
    val header: KJnaBinder.Header,
    val getStructImport: (String) -> String,
    val getEnumImport: (String) -> String
) {
    private val imports: MutableList<Pair<String, String?>> = mutableListOf()

    fun generateFile(target: KJnaBinderTarget): String {
        imports.clear()

        val function_headers: List<String> = header.info.functions.map { getKotlinFunctionHeader(it) }

        val file_content: StringBuilder = StringBuilder()
        file_content.apply {
            appendLine("package ${header.package_name}")
            appendLine()

            if (imports.isNotEmpty()) {
                for ((import, alias) in imports.distinct().sortedBy { it.first }) {
                    append("import $import")
                    if (alias != null && alias != import) {
                        append(" as $alias")
                    }
                    appendLine()
                }
                appendLine()
            }

            for (modifier in target.getClassModifiers()) {
                append(modifier)
                append(" ")
            }
            appendLine("object ${header.class_name} {")

            for ((index, function) in header.info.functions.withIndex()) {
                try {
                    val function_header: String = function_headers[index]
                    append("    ")
                    append(target.implementKotlinFunction(function, function_header, this@BinderFileGenerator))
                    appendLine()
                }
                catch (e: Throwable) {
                    throw RuntimeException("Generating function $function in $header failed", e)
                }
            }

            appendLine("}")
        }

        println("-----BEGIN FILE-----")
        println(file_content.toString())
        println("------END FILE------")

        TODO()
    }

    private fun importStruct(struct_name: String, alias: String? = null) {
        imports.add(Pair(getStructImport(struct_name), alias))
    }

    private fun importEnum(enum_name: String, alias: String? = null) {
        imports.add(Pair(getEnumImport(enum_name), alias))
    }

    private fun createParameterUnion(function_name: String, parameter_name: String?, union: CType.Union): String {
        TODO("$function_name $parameter_name $union")
    }

    private fun getRuntimeType(cls: KClass<*>): String {
        val import_coordinates: String = cls.qualifiedName!!
        if (imports.none { it.first == import_coordinates }) {
            imports.add(Pair(import_coordinates, null))
        }
        return cls.simpleName!!
    }

    private fun getKotlinFunctionHeader(function: CFunctionDeclaration): String {
        val header: StringBuilder = StringBuilder("fun ${function.name}(")

        val used_param_names: MutableList<String> = function.parameters.mapNotNull { it.name }.toMutableList()
        for ((index, param) in function.parameters.withIndex()) {
            val param_name: String
            if (param.name != null) {
                param_name = param.name
            }
            else {
                var offset: Int = 0
                while (used_param_names.contains("p${index + offset}")) {
                    offset++
                }

                param_name = "p${index + offset}"
                used_param_names.add(param_name)
            }

            header.append(param_name)
            header.append(": ")
            header.append(param.type.toKotlinTypeName(false) { createParameterUnion(function.name, param_name, it) })

            if (index + 1 != function.parameters.size) {
                header.append(", ")
            }
        }

        header.append(")")

        val return_type: String = function.return_type.toKotlinTypeName(true) { createParameterUnion(function.name, null, it) }
        if (return_type != "Unit") {
            header.append(": ")
            header.append(return_type)
        }

        return header.toString()
    }

    private fun CValueType?.toKotlinTypeName(
        is_return_type: Boolean,
        createUnion: (CType.Union) -> String
    ): String {
        if (this == null) {
            return "Unit"
        }

        var (type: String?, pointer_depth: Int) =
            type.toKotlinType(pointer_depth, createUnion = createUnion)

        if (type == null) {
            if (is_return_type) {
                return "Unit"
            }

            type = "Any"
        }

        while (pointer_depth >= 2) {
            pointer_depth -= 2
            type = "Array<$type>"
        }

        while (pointer_depth-- > 0) {
            if (type == "Any") {
                type = getRuntimeType(KJnaVoidPointer::class)
            }
            else {
                type = getRuntimeType(KJnaPointer::class) + "<$type>"
            }
        }

        check(pointer_depth <= 0)
        return type!!
    }

    private fun CType.toKotlinType(pointer_depth: Int, createUnion: (CType.Union) -> String): Pair<String?, Int> {
        val kotlin_type: String?
        var pointer_depth: Int = pointer_depth

        when (this) {
            is CType.Primitive ->
                when (this) {
                    CType.Primitive.VOID -> kotlin_type = null
                    CType.Primitive.CHAR,
                    CType.Primitive.U_CHAR -> {
                        if (pointer_depth > 0) {
                            kotlin_type = "String"
                            pointer_depth--
                        }
                        else {
                            kotlin_type = "Char"
                        }
                    }
                    CType.Primitive.SHORT,
                    CType.Primitive.U_SHORT -> kotlin_type = "Short"
                    CType.Primitive.INT,
                    CType.Primitive.U_INT -> kotlin_type = "Int"
                    CType.Primitive.LONG,
                    CType.Primitive.U_LONG,
                    CType.Primitive.LONG_LONG,
                    CType.Primitive.U_LONG_LONG -> kotlin_type = "Long"
                    CType.Primitive.FLOAT -> kotlin_type = "Float"
                    CType.Primitive.DOUBLE,
                    CType.Primitive.LONG_DOUBLE -> kotlin_type = "Double"
                    CType.Primitive.BOOL -> kotlin_type = "Boolean"
                }

            is CType.TypeDef -> {
                val resolved_type: CValueType = resolve(binder.typedefs)

                when (resolved_type.type) {
                    is CType.Struct -> {
                        importStruct(resolved_type.type.name, name)
                        kotlin_type = name
                        pointer_depth += resolved_type.pointer_depth
                    }
                    is CType.Enum -> {
                        importEnum(resolved_type.type.name, name)
                        kotlin_type = name
                        pointer_depth += resolved_type.pointer_depth
                    }
                    is CType.Union -> throw IllegalStateException(resolved_type.toString())
                    else -> {
                        resolved_type.type.toKotlinType(pointer_depth + resolved_type.pointer_depth, { throw IllegalStateException("Union") }).apply {
                            kotlin_type = first
                            pointer_depth = second
                        }
                    }
                }

            }

            is CType.Struct -> {
                importStruct(name)
                kotlin_type = name
            }

            is CType.Enum -> {
                importEnum(name)
                kotlin_type = name
            }

            is CType.Union -> {
                kotlin_type = createUnion(this)
            }
        }

        return Pair(kotlin_type, pointer_depth)
    }
}
