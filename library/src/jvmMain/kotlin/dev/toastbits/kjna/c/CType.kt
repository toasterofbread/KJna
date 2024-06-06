package dev.toastbits.kjna.c

import dev.toastbits.kjna.grammar.*
import kotlinx.serialization.Serializable

@Serializable
sealed interface CType {
    fun isForwardDeclaration(): Boolean = false

    fun isChar(): Boolean = this == Primitive.CHAR || this == Primitive.U_CHAR

    @Serializable
    enum class Primitive: CType {
        VOID,
        CHAR,
        U_CHAR,
        SHORT,
        U_SHORT,
        INT,
        U_INT,
        LONG,
        U_LONG,
        LONG_LONG,
        U_LONG_LONG,
        FLOAT,
        DOUBLE,
        LONG_DOUBLE,
        BOOL,
        VALIST;

        fun isInteger(): Boolean =
            when (this) {
                SHORT,
                U_SHORT,
                INT,
                U_INT,
                LONG,
                U_LONG,
                LONG_LONG,
                U_LONG_LONG -> true
                else -> false
            }
    }

    @Serializable
    data class Typedef(val name: String): CType

    @Serializable
    data class Struct(val name: String?, val definition: CStructDefinition?, val anonymous_index: Int?): CType {
        init {
            require((name != null) != (anonymous_index != null)) { this }
        }
        override fun isForwardDeclaration(): Boolean = definition == null
    }

    @Serializable
    data class Array(val type: CValueType, val size: Int): CType

    @Serializable
    data class Union(val name: String?, val values: Map<String, CValueType>?, val anonymous_index: Int?): CType {
        init {
            require((name != null) != (anonymous_index != null)) { this }
        }
        override fun isForwardDeclaration(): Boolean = values == null
    }

    @Serializable
    data class Enum(val name: String, val values: Map<String, Int>, val has_explicit_value: Boolean, val type_name: String?): CType

    @Serializable
    data class Function(val shape: CFunctionDeclaration, val data_params: DataParams? = null): CType {
        @Serializable
        data class DataParams(val send: Int, val recv: Int)

        companion object {
            val FUNCTION_DATA_PARAM_TYPE: CValueType = CValueType(CType.Primitive.VOID, 1)
        }
    }
}

fun PackageGenerationScope.parseDeclarationSpecifierType(declaration_specifiers: List<CParser.DeclarationSpecifierContext>, name: String? = null): CType {
    require(declaration_specifiers.isNotEmpty())

    val qualifiers: MutableList<CParser.TypeQualifierContext> = mutableListOf()
    val specifiers: MutableList<CParser.TypeSpecifierContext> = mutableListOf()

    for (specifier in declaration_specifiers) {
        specifier.typeQualifier()?.also { qualifiers.add(it) }
        specifier.typeSpecifier()?.also { specifiers.add(it) }
    }

    try {
        return parseType(specifiers, qualifiers, name)
    }
    catch (e: Throwable) {
        throw RuntimeException("parseType failed ($name, ${declaration_specifiers.map { it.text }})", e)
    }
}

fun PackageGenerationScope.parseType(specifiers: List<CParser.TypeSpecifierContext>, qualifiers: List<CParser.TypeQualifierContext> = emptyList(), name: String? = null): CType {
    require(specifiers.isNotEmpty())

    val modifiers: List<CTypeModifier> = specifiers.dropLast(1).mapNotNull { parseModifierSpecifier(it) }
    var type: CType =
        try {
            parseTypeSpecifier(specifiers.last(), name)
        }
        catch (e: Throwable) {
            throw RuntimeException("parseTypeSpecifier failed ($name, ${specifiers.map { it.text }})", e)
        }

    for (modifier in modifiers) {
        when (modifier) {
            CTypeModifier.SHORT -> {
                when (type) {
                    CType.Primitive.INT -> type = CType.Primitive.SHORT
                    CType.Primitive.U_INT -> type = CType.Primitive.U_SHORT
                    CType.Primitive.LONG -> type = CType.Primitive.INT
                    CType.Primitive.U_LONG -> type = CType.Primitive.U_INT
                    CType.Primitive.LONG_LONG -> type = CType.Primitive.LONG
                    CType.Primitive.U_LONG_LONG -> type = CType.Primitive.U_LONG
                    else -> {}
                }
            }
            CTypeModifier.LONG -> {
                when (type) {
                    CType.Primitive.SHORT -> type = CType.Primitive.INT
                    CType.Primitive.U_SHORT -> type = CType.Primitive.U_INT
                    CType.Primitive.INT -> type = CType.Primitive.LONG
                    CType.Primitive.U_INT -> type = CType.Primitive.U_LONG
                    CType.Primitive.LONG -> type = CType.Primitive.LONG_LONG
                    CType.Primitive.U_LONG -> type = CType.Primitive.U_LONG_LONG
                    else -> {}
                }
            }
            CTypeModifier.SIGNED -> {
                when (type) {
                    CType.Primitive.U_CHAR -> type = CType.Primitive.CHAR
                    CType.Primitive.U_SHORT -> type = CType.Primitive.SHORT
                    CType.Primitive.U_INT -> type = CType.Primitive.INT
                    CType.Primitive.U_LONG -> type = CType.Primitive.LONG
                    CType.Primitive.U_LONG_LONG -> type = CType.Primitive.LONG_LONG
                    else -> {}
                }
            }
            CTypeModifier.UNSIGNED -> {
                when (type) {
                    CType.Primitive.CHAR -> type = CType.Primitive.U_CHAR
                    CType.Primitive.SHORT -> type = CType.Primitive.U_SHORT
                    CType.Primitive.INT -> type = CType.Primitive.U_INT
                    CType.Primitive.LONG -> type = CType.Primitive.U_LONG
                    CType.Primitive.LONG_LONG -> type = CType.Primitive.U_LONG_LONG
                    else -> {}
                }
            }
        }
    }

    // TODO | Apply qualifiers
    return type
}

private fun PackageGenerationScope.parseTypeSpecifier(type_specifier: CParser.TypeSpecifierContext, name: String? = null): CType {
    if (type_specifier.Void() != null) {
        return CType.Primitive.VOID
    }
    if (type_specifier.Char() != null) {
        return CType.Primitive.CHAR
    }
    if (type_specifier.Short() != null) {
        return CType.Primitive.SHORT
    }
    if (type_specifier.Int() != null) {
        return CType.Primitive.INT
    }
    if (type_specifier.Long() != null) {
        return CType.Primitive.LONG
    }
    if (type_specifier.Float() != null) {
        return CType.Primitive.FLOAT
    }
    if (type_specifier.Double() != null) {
        return CType.Primitive.DOUBLE
    }
    if (type_specifier.Bool() != null) {
        return CType.Primitive.BOOL
    }
    if (type_specifier.Unsigned() != null) {
        return CType.Primitive.INT
    }

    type_specifier.typedefName()?.Identifier()?.text?.also {
        return CType.Typedef(it)
    }

    type_specifier.structOrUnionSpecifier()?.also { specifier ->
        val struct_declarations: List<CParser.StructDeclarationContext>? = specifier.structDeclarationList()?.structDeclaration()
        val struct_definition: CStructDefinition? = struct_declarations?.let { parseStructDefinition(it) }

        val struct_or_union: CParser.StructOrUnionContext = specifier.structOrUnion()
        val name: String? = specifier.Identifier()?.text ?: name
        if (struct_or_union.Struct() != null) {
            return CType.Struct(name, struct_definition, if (name == null) ++anonymous_struct_index else null)
        }
        else if (struct_or_union.Union() != null) {
            return CType.Union(name, struct_definition?.fields, if (name == null) ++anonymous_struct_index else null)
        }
        else {
            throw NotImplementedError(type_specifier.text)
        }
    }

    type_specifier.enumSpecifier()?.also { enum_specifier ->
        val enum_name: String = enum_specifier.Identifier()?.text ?: name ?: throw NullPointerException(type_specifier.text)
        val values: MutableMap<String, Int> = mutableMapOf()

        var previous_value: Int = -1
        var has_explicit_value: Boolean = false

        for (item in enum_specifier.enumeratorList()?.enumerator().orEmpty()) {
            val key: String = item.enumerationConstant().Identifier().text
            val value: Int

            val constant_expression: CParser.ConstantExpressionContext? = item.constantExpression()
            if (constant_expression != null) {
                has_explicit_value = true
                value = parseConstantExpression(constant_expression.text, values, parser)
            }
            else {
                value = (previous_value + 1)
            }

            values[key] = value
            previous_value = value
        }

        return CType.Enum(
            name = enum_name,
            values = values,
            has_explicit_value = has_explicit_value,
            type_name = if (enum_specifier.Identifier() == null) null else name
        )
    }

    TODO(type_specifier.text)
}

private fun parseConstantExpression(expression: String, values: Map<String, Int>, parser: CHeaderParser): Int {
    try {
        return 0

        // TODO | Uses an insane amount of memory?
        // return expression.tryAllOperations(values, parser)!!
    }
    catch (e: Throwable) {
        throw RuntimeException("Parsing constant value expression '$expression' failed ($values)", e)
    }
}

private enum class Operation {
    SHIFT_L,
    SHIFT_R,
    AND,
    OR,
    ADD,
    SUB,
    MUL,
    DIV;

    val kw: String get() =
        when (this) {
            SHIFT_L -> "<<"
            SHIFT_R -> ">>"
            AND -> "&"
            OR -> "|"
            ADD -> "+"
            SUB -> "-"
            MUL -> "*"
            DIV -> "/"
        }

    fun perform(a: Int, b: Int): Int =
        when (this) {
            SHIFT_L -> a shl b
            SHIFT_R -> a shr b
            AND -> a and b
            OR -> a or b
            ADD -> a + b
            SUB -> a - b
            MUL -> a * b
            DIV -> a / b
        }
}

private fun String.tryAllOperations(values: Map<String, Int>, parser: CHeaderParser): Int? {
    check(isNotEmpty()) { "Empty expression" }

    val expression: String
    val multiplier: Int

    if (first() == '-') {
        expression = this.drop(1)
        multiplier = -1
    }
    else {
        expression = this
        multiplier = 1
    }

    if (expression.firstOrNull()?.isDigit() == true) {
        val last_digit: Int = expression.indexOfLast { it.isDigit() }
        if (last_digit != -1) {
            expression.substring(0, last_digit + 1).toIntOrNull()?.let { return it * multiplier }
        }
    }

    if (expression.startsWith("0x")) {
        @OptIn(ExperimentalStdlibApi::class)
        return expression.drop(2).hexToInt() * multiplier
    }

    if (expression.startsWith("0b")) {
        return expression.drop(2).toInt(2) * multiplier
    }

    if (expression.length == 3 && expression.first() == '\'' && expression.last() == '\'' ) {
        return get(1).code * multiplier
    }

    values[expression]?.also { return it * multiplier }

    parser.getConstantExpressionValue(expression)?.also { return it * multiplier }

    // -----

    val seq: MutableList<Any> = mutableListOf()

    var i: Int = 0
    var start: Int = 0
    while (i < length) {
        if (get(i) == '(') {
            val end: Int = indexOf(')', i + 1)
            check(end != -1)

            seq.add(substring(i + 1, end))
            i = end + 1
            start = i
            continue
        }

        for (op in Operation.entries) {
            if (i + op.kw.length < length && substring(i, i + op.kw.length) == op.kw) {
                val between: String = substring(start, i)
                if (between.isNotBlank()) {
                    seq.add(between)
                }

                seq.add(op)

                i += op.kw.length - 1
                start = i + 1
                break
            }
        }

        i++
    }

    val end: String = substring(start)
    if (end.isNotBlank()) {
        seq.add(end)
    }

    check(seq.isNotEmpty())


    val initial_seq: List<Any> = seq.toList()

    try {
        var a: Int? = null
        var op: Operation? = null

        while (seq.isNotEmpty()) {
            val part: Any = seq.removeFirst()
            if (seq.isEmpty()) {
                check(part is String) { part }
                return part.tryAllOperations(values, parser)
            }

            if (a == null) {
                a = when (part) {
                    is Int -> part
                    is String -> part.tryAllOperations(values, parser)
                    else -> throw IllegalStateException(part.toString())
                }
            }
            else if (op == null) {
                check(part is Operation)
                op = part
            }
            else {
                val b: Int = when (part) {
                    is Int -> part
                    is String -> part.tryAllOperations(values, parser) ?: throw NullPointerException(part)
                    else -> throw IllegalStateException(part.toString())
                }

                val value: Int = op.perform(a, b)
                if (seq.isEmpty()) {
                    return value
                }

                seq.add(0, value)
                a = null
                op = null
            }
        }
    }
    catch (e: Throwable) {
        throw RuntimeException("Parsing sequence failed ($seq, $this, initial=$initial_seq)", e)
    }

    throw NotImplementedError(this)
}

private fun parseModifierSpecifier(type_specifier: CParser.TypeSpecifierContext): CTypeModifier? {
    if (type_specifier.Short() != null) {
        return CTypeModifier.SHORT
    }
    if (type_specifier.Long() != null) {
        return CTypeModifier.LONG
    }
    if (type_specifier.Signed() != null) {
        return CTypeModifier.SIGNED
    }
    if (type_specifier.Unsigned() != null) {
        return CTypeModifier.UNSIGNED
    }

    // println("Ignoring unknown type modifier '${type_specifier.text}'")
    return null
}
