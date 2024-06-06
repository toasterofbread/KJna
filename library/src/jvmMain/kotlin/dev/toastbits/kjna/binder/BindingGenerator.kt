package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.*
import dev.toastbits.kjna.c.fullyResolve
import dev.toastbits.kjna.runtime.RuntimeType
import dev.toastbits.kjna.binder.target.KJnaBindTarget
import dev.toastbits.kjna.binder.target.KJnaBindTargetDisabled
import dev.toastbits.kjna.binder.target.KJnaBindTargetShared
import kotlin.reflect.KClass
import kotlin.contracts.contract
import kotlin.contracts.InvocationKind

class BindingGenerator(
    val binder: KJnaBinder,
    var getStructImport: (String) -> String,
    var getUnionImport: (String) -> String,
    var getEnumImport: (String) -> String,
    val anonymous_struct_indices: Map<Int, Int> = emptyMap()
) {
    fun buildKotlinFile(
        target: KJnaBindTarget,
        package_coordinates: String,
        buildContent: StringBuilderGenerationScope.() -> Unit
    ) {
        val imports: MutableList<Import> = mutableListOf()
        val local_classes: MutableMap<String, String> = mutableMapOf()
        val annotations: MutableList<String> = mutableListOf()

        val scope: StringBuilderGenerationScope =
            object : StringBuilderGenerationScope(target, imports, local_classes, annotations) {
                override fun build(): String {
                    val content: String = this.toString()
                    return buildString {
                        if (annotations.isNotEmpty()) {
                            for (annotation in annotations) {
                                append(annotation)
                            }
                            appendLine()
                        }
                        appendLine("package $package_coordinates")

                        appendLine()
                        append(generateImportBlock(imports, binder, anonymous_struct_indices))

                        append(content)

                        appendLine()

                        if (local_classes.isNotEmpty()) {
                            for ((_, union_content) in local_classes) {
                                appendLine()
                                appendLine(union_content)
                            }
                        }
                    }
                }
            }

        buildContent(scope)
    }

    fun unhandledGenerationScope(target: KJnaBindTarget, generate: GenerationScope.() -> Unit) {
        val scope: GenerationScope = GenerationScope(target, mutableListOf(), mutableMapOf(), mutableListOf())
        generate(scope)
    }

    inner abstract class StringBuilderGenerationScope(
        target: KJnaBindTarget,
        imports: MutableList<Import>,
        local_classes: MutableMap<String, String>,
        annotations: MutableList<String>
    ): GenerationScope(target, imports, local_classes, annotations) {
        private val string: StringBuilder = StringBuilder()

        override fun toString(): String = string.toString()

        fun append(content: Any) { string.append(content) }
        fun appendLine() { string.appendLine() }
        fun appendLine(content: Any) { string.appendLine(content) }

        abstract fun build(): String
    }

    inner open class GenerationScope(
        val target: KJnaBindTarget,
        private val imports: MutableList<Import>,
        private val local_classes: MutableMap<String, String>,
        private val annotations: MutableList<String>
    ) {
        val binder: KJnaBinder get() = this@BindingGenerator.binder

        fun addImport(import: Import) {
            if (!imports.contains(import)) {
                imports.add(import)
            }
        }

        fun importFromCoordinates(coordinates: String, alias: String? = null) {
            check(!coordinates.contains(" ")) { coordinates }
            addImport(Import.Coordinates(coordinates, alias))
        }

        fun addContainerAnnotation(annotation: String) {
            if (!annotations.contains(annotation)) {
                annotations.add(annotation)
            }
        }

        fun getFunctionParameterName(name: String?, index: Int, used_names: List<String>): String {
            if (name != null && !used_names.contains(name)) {
                return name
            }

            val base: String = name ?: "p"

            var offset: Int = 0
            while (used_names.contains(base + (index + offset).toString())) {
                offset++
            }

            return base + (index + offset).toString()
        }

        fun generateHeaderFileContent(header: KJnaBinder.Header, all_structs: List<CType.Struct>?): String =
            buildString {
                val class_modifiers: List<String> = target.getClassModifiers()
                for (modifier in class_modifiers) {
                    append(modifier)
                    append(' ')
                }

                val header_constructor: String = target.implementHeaderConstructor(this@GenerationScope) ?: ""
                appendLine("class ${header.class_name}$header_constructor {")

                val initialiser: String? = target.implementHeaderInitialiser(all_structs, this@GenerationScope)
                if (initialiser != null) {
                    appendLine(initialiser.prependIndent("    "))
                    appendLine()
                }

                for ((name, function) in binder.package_info.headers[header.absolute_path]!!.functions) {
                    try {
                        val function_header: String = getKotlinFunctionHeader(function)
                        appendLine(target.implementFunction(function, function_header, header.class_name, this@GenerationScope).prependIndent("    "))
                    }
                    catch (e: Throwable) {
                        throw RuntimeException("Generating function $function in $header failed", e)
                    }
                }

                appendLine(
                    buildString {
                        appendLine()

                        for (modifier in class_modifiers) {
                            if (modifier == "expect") {
                                continue
                            }
                            append(modifier)
                            append(' ')
                        }
                        appendLine("companion object {")

                        appendLine(
                            when (target) {
                                is KJnaBindTargetShared -> "fun isAvailable(): Boolean"
                                is KJnaBindTargetDisabled -> "actual fun isAvailable(): Boolean = false"
                                else -> "actual fun isAvailable(): Boolean = true"
                            }.prependIndent("    ")
                        )
                        append("}")
                    }.prependIndent("    ")
                )

                append("}")
            }

        fun getKotlinFunctionHeader(function: CFunctionDeclaration): String {
            val header: StringBuilder = StringBuilder("fun ${function.name}(")

            val used_param_names: MutableList<String> = mutableListOf()
            val function_data_param_indices: List<Int> = function.parameters.mapNotNull { param ->
                if (param.type.type is CType.Function) {
                    return@mapNotNull param.type.type.data_params?.send
                }
                return@mapNotNull null
            }

            val params: List<CFunctionParameter> = function.parameters.filterIndexed { index, _ -> !function_data_param_indices.contains(index) }

            for ((index, param) in params.withIndex()) {
                val param_name: String = getFunctionParameterName(param.name, index, used_param_names)
                used_param_names.add(param_name)

                val param_type: String? =
                    param.type.toKotlinTypeName(
                        false,
                        createUnion = { createUnion(function.name, param_name, index, it) },
                        createStruct = { createStruct(function.name, param_name, index, it) }
                    )
                if (param_type == null) {
                    if (params.size == 1) {
                        break
                    }

                    throw RuntimeException("void used on its own $function")
                }

                header.append(param_name)
                header.append(": ")
                header.append(param_type)

                if (index + 1 != params.size) {
                    header.append(", ")
                }
            }

            header.append(")")

            val return_type: String =
                function.return_type.toKotlinTypeName(
                    true,
                    createUnion = { createUnion(function.name, null, null, it) },
                    createStruct = { createStruct(function.name, null, null, it) }
                )!!

            if (return_type != "Unit") {
                header.append(": ")
                header.append(return_type)

                if (function.return_type?.type?.isChar() == true && header.last() != '?') {
                    header.append('?')
                }
            }

            return header.toString()
        }

        fun CValueType?.toKotlinTypeName(
            is_return_type: Boolean,
            createUnion: (CType.Union) -> String,
            createStruct: (CType.Struct) -> String
        ): String? {
            if (this == null) {
                return "Unit"
            }

            val actual_type: CValueType = fullyResolve(binder)

            var (type: String?, pointer_depth: Int) =
                type.toKotlinType(pointer_depth, is_return_type = is_return_type, createUnion = createUnion, createStruct = createStruct)

            if (actual_type.type is CType.Function) {
                return type!!
            }

            if (type == null && pointer_depth == 0) {
                if (is_return_type) {
                    return "Unit"
                }
                else {
                    return null
                }
            }

            while (pointer_depth > 0) {
                pointer_depth--

                if (type == null) {
                    type = importRuntimeType(RuntimeType.KJnaPointer) + "?"
                }
                else {
                    type = importRuntimeType(RuntimeType.KJnaTypedPointer) + "<$type>?"
                }
            }

            if (type == "String") {
                type = "String?"
            }

            check(pointer_depth == 0) { this }
            return type!!
        }

        private fun CType.toKotlinType(pointer_depth: Int, createUnion: (CType.Union) -> String, createStruct: (CType.Struct) -> String, is_return_type: Boolean = false): Pair<String?, Int> {
            val kotlin_type: String?
            var pointer_depth: Int = pointer_depth

            try {
                when (this) {
                    is CType.Primitive ->
                        when (this) {
                            CType.Primitive.VOID -> kotlin_type = null
                            CType.Primitive.U_CHAR -> kotlin_type = "Char"
                            CType.Primitive.CHAR -> {
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
                            CType.Primitive.VALIST -> kotlin_type = importRuntimeType(RuntimeType.KJnaVarargList)
                        }

                    is CType.Typedef -> {
                        val resolved_type: CValueType = resolve(binder.typedefs)

                        when (resolved_type.type) {
                            is CType.Struct -> {
                                kotlin_type = importStruct(resolved_type.type.name ?: throw NullPointerException("$this $resolved_type"))
                                pointer_depth += resolved_type.pointer_depth
                            }
                            is CType.Enum -> {
                                kotlin_type = importEnum(resolved_type.type.name)
                                pointer_depth += resolved_type.pointer_depth
                            }
                            is CType.Union -> {
                                kotlin_type = importUnion(resolved_type.type.name ?: throw NullPointerException("$this $resolved_type"))
                                pointer_depth += resolved_type.pointer_depth
                            }
                            else -> {
                                resolved_type.type.toKotlinType(
                                    pointer_depth + resolved_type.pointer_depth,
                                    is_return_type = is_return_type,
                                    createUnion = { throw IllegalStateException("Union $it") },
                                    createStruct = { throw IllegalStateException("Struct $it") }
                                ).apply {
                                    kotlin_type = first
                                    pointer_depth = second
                                }
                            }
                        }
                    }

                    is CType.Struct -> {
                        kotlin_type = name?.let { importStruct(name) } ?: createStruct(this)
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
                            if (is_return_type && pointer_depth > 1) {
                                val KJnaTypedPointer: String = importRuntimeType(RuntimeType.KJnaTypedPointer)
                                for (i in 0 until pointer_depth - 1) {
                                    append("$KJnaTypedPointer<")
                                }
                            }

                            append('(')
                            for ((index, param) in shape.parameters.withIndex()) {
                                if (param.name != null) {
                                    append(param.name)
                                    append(": ")
                                }
                                append(param.type.toKotlinTypeName(false, createUnion, createStruct))

                                if (index + 1 != shape.parameters.size) {
                                    append(", ")
                                }
                            }

                            append(") -> ")
                            append(shape.return_type.toKotlinTypeName(true, createUnion, createStruct))

                            if (is_return_type) {
                                for (i in 0 until pointer_depth - 1) {
                                    append(">")
                                }
                            }
                        }
                    }

                    is CType.Array -> {
                        kotlin_type = "Array<${type.toKotlinTypeName(false, createUnion, createStruct)}>"
                    }
                }
            }
            catch (e: Throwable) {
                throw RuntimeException("toKotlinType failed ($this $pointer_depth)", e)
            }

            return Pair(kotlin_type, pointer_depth)
        }

        fun importStruct(struct_name: String, alias: String? = null): String {
            importFromCoordinates(getStructImport(struct_name), alias)
            return alias ?: struct_name
        }

        private fun importEnum(enum_name: String, alias: String? = null): String {
            importFromCoordinates(getEnumImport(enum_name), alias)
            return alias ?: enum_name
        }

        private fun importUnion(union_name: String, alias: String? = null): String {
            importFromCoordinates(getUnionImport(union_name), alias)
            return alias ?: union_name
        }

        fun createUnion(scope_name: String, parameter_name: String?, parameter_index: Int?, union: CType.Union): String {
            val name: String = getLocalClassName(scope_name, parameter_index)
            check(!local_classes.contains(name)) { "Union name collision '$name'" }

            local_classes[name] = generateUnion(name, union, parameter_name, scope_name, target)
            return name
        }

        fun createStruct(scope_name: String, parameter_name: String?, parameter_index: Int?, struct: CType.Struct): String {
            val name: String = getLocalClassName(scope_name, parameter_index)
            check(!local_classes.contains(name)) { "Struct name collision '$name'" }

            local_classes[name] = generateStructBody(name, struct, target, scope_name)
            return name
        }

        fun getLocalClassName(scope_name: String, parameter_index: Int?): String {
            return scope_name + "_union_" + parameter_index?.toString().orEmpty()
        }

        fun importRuntimeType(type: RuntimeType): String {
            importFromCoordinates(type.coordinates)
            return type.name
        }
    }

    interface Import {
        fun getImportCoordinates(binder: KJnaBinder): String
        fun getName(): String
        fun getAlias(): String? = null

        data class Coordinates(val import_coordinates: String, val import_alias: String? = null): Import {
            override fun getImportCoordinates(binder: KJnaBinder): String = import_coordinates
            override fun getName(): String = import_alias ?: import_coordinates.split(".").last()
            override fun getAlias(): String? = import_alias
        }
    }
}
