package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.*
import dev.toastbits.kjna.runtime.RuntimeType
import kotlin.reflect.KClass
import kotlin.contracts.contract
import kotlin.contracts.InvocationKind

class BindingGenerator(
    val binder: KJnaBinder,
    var getStructImport: (String) -> String,
    var getEnumImport: (String) -> String
) {
    @OptIn(kotlin.contracts.ExperimentalContracts::class)
    fun generationScope(action: GenerationScope.() -> Unit): List<Pair<String, String?>> {
        contract {
            callsInPlace(action, InvocationKind.EXACTLY_ONCE)
        }

        val imports: MutableList<Pair<String, String?>> = mutableListOf()
        action(GenerationScope(imports))
        return imports
    }

    fun generateHeaderFile(header: KJnaBinder.Header, target: KJnaBinderTarget): String {
        val functions: List<String>
        val imports: List<Pair<String, String?>> = generationScope {
            functions = header.info.functions.map { function ->
                try {
                    val header: String = getKotlinFunctionHeader(function)
                    return@map target.implementKotlinFunction(function, header, this)
                }
                catch (e: Throwable) {
                    throw RuntimeException("Generating function $function in $header failed", e)
                }
            }
        }

        return buildString {
            appendLine("package ${header.package_name}")
            appendLine()

            append(generateImportBlock(imports))

            for (modifier in target.getClassModifiers()) {
                append(modifier)
                append(" ")
            }
            appendLine("object ${header.class_name} {")

            for (function in functions) {
                append("    ")
                appendLine(function)
            }

            appendLine("}")
        }
    }

    inner class GenerationScope(
        private val imports: MutableList<Pair<String, String?>>
    ) {
        val binder: KJnaBinder get() = this@BindingGenerator.binder

        fun importFromCoordinates(coordinates: String, alias: String? = null) {
            imports.add(Pair(coordinates, alias))
        }

        fun getKotlinFunctionHeader(function: CFunctionDeclaration): String {
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

                val param_type: String? = param.type.toKotlinTypeName(false) { createParameterUnion(function.name, param_name, it) }
                if (param_type == null) {
                    if (function.parameters.size == 1) {
                        break
                    }

                    throw RuntimeException("void used on its own $function")
                }

                header.append(param_name)
                header.append(": ")
                header.append(param_type)

                if (index + 1 != function.parameters.size) {
                    header.append(", ")
                }
            }

            header.append(")")

            val return_type: String = function.return_type.toKotlinTypeName(true) { createParameterUnion(function.name, null, it) }!!
            if (return_type != "Unit") {
                header.append(": ")
                header.append(return_type)
            }

            return header.toString()
        }

        fun CValueType?.toKotlinTypeName(
            is_return_type: Boolean,
            createUnion: (CType.Union) -> String
        ): String? {
            if (this == null) {
                return "Unit"
            }

            var (type: String?, pointer_depth: Int) =
                type.toKotlinType(pointer_depth, createUnion = createUnion)

            if (type == null) {
                if (is_return_type) {
                    return "Unit"
                }

                if (pointer_depth == 0) {
                    return null
                }
            }

            while (pointer_depth-- > 0) {
                if (type == null) {
                    type = importRuntimeType(RuntimeType.KJnaPointer) + "?"
                }
                else {
                    type = importRuntimeType(RuntimeType.KJnaTypedPointer) + "<$type>?"
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
                        CType.Primitive.SHORT -> kotlin_type = "Short"
                        CType.Primitive.U_SHORT -> kotlin_type = "UShort"
                        CType.Primitive.INT -> kotlin_type = "Int"
                        CType.Primitive.U_INT -> kotlin_type = "UInt"
                        CType.Primitive.LONG,
                        CType.Primitive.LONG_LONG -> kotlin_type = "Long"
                        CType.Primitive.U_LONG,
                        CType.Primitive.U_LONG_LONG -> kotlin_type = "ULong"
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

                is CType.Function -> {
                    kotlin_type = buildString {
                        append('(')
                        for ((index, param) in shape.parameters.withIndex()) {
                            if (param.name != null) {
                                append(param.name)
                                append(": ")
                            }
                            append(param.type.toKotlinTypeName(false, createUnion))

                            if (index + 1 != shape.parameters.size) {
                                append(", ")
                            }
                        }

                        append(") -> ")
                        append(shape.return_type.toKotlinTypeName(true, createUnion))
                    }
                }
            }

            return Pair(kotlin_type, pointer_depth)
        }

        private fun importStruct(struct_name: String, alias: String? = null) {
            importFromCoordinates(getStructImport(struct_name), alias)
        }

        private fun importEnum(enum_name: String, alias: String? = null) {
            importFromCoordinates(getEnumImport(enum_name), alias)
        }

        private fun createParameterUnion(function_name: String, parameter_name: String?, union: CType.Union): String {
            TODO("$function_name $parameter_name $union")
        }

        fun importRuntimeType(type: RuntimeType): String {
            if (imports.none { it.first == type.coordinates }) {
                importFromCoordinates(type.coordinates, null)
            }
            return type.name
        }
    }
}
