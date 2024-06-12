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

    sealed interface StructOrUnion: CType {
        val name: String?
        val anonymous_index: Int?
    }

    @Serializable
    data class Struct(override val name: String?, val definition: CStructDefinition?, override val anonymous_index: Int?): CType, StructOrUnion {
        init {
            require((name != null) != (anonymous_index != null)) { this }
        }
        override fun isForwardDeclaration(): Boolean = definition == null
    }

    @Serializable
    data class Union(override val name: String?, val values: Map<String, CValueType>?, override val anonymous_index: Int?): CType, StructOrUnion {
        init {
            require((name != null) != (anonymous_index != null)) { this }
        }
        override fun isForwardDeclaration(): Boolean = values == null
    }

    @Serializable
    data class Enum(val name: String, val values: Map<String, Int>, val has_explicit_value: Boolean, val type_name: String?): CType

    @Serializable
    data class Function(val shape: CFunctionDeclaration, val data_params: DataParams? = null, val typedef_name: String? = null): CType {
        @Serializable
        data class DataParams(val send: Int, val recv: Int)

        companion object {
            val FUNCTION_DATA_PARAM_TYPE: CValueType = CValueType(CType.Primitive.VOID, 1)
        }
    }

    @Serializable
    data class Array(val type: CValueType, val size: Int): CType
}

internal fun PackageGenerationScope.parseDeclarationSpecifierType(declaration_specifiers: List<CParser.DeclarationSpecifierContext>, name: String? = null): CType {
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

internal fun PackageGenerationScope.parseType(specifiers: List<CParser.TypeSpecifierContext>, qualifiers: List<CParser.TypeQualifierContext> = emptyList(), name: String? = null): CType {
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
        return expression.tryAllOperations(values, parser, 0)!!
    }
    catch (e: Throwable) {
        throw RuntimeException("Parsing constant value expression '$expression' failed ($values)", e)
    }
}

private enum class SingleOperation {
    NOT;

    val kw: String get() =
        when (this) {
            NOT -> "~"
        }

    fun perform(value: Int): Int =
        when (this) {
            NOT -> value.inv()
        }
}

private enum class DoubleOperation {
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

private fun String.tryAllOperations(values: Map<String, Int>, parser: CHeaderParser, depth: Int, region: IntRange = indices): Int? {
    check(region.size != 0) { "Empty expression ($region, $this)" }
    check(depth < 100) { "tryAllOperations recursion depth reached 100 ($region, $this)" }

    var region: IntRange = region
    val multiplier: Int

    if (get(region.start) == '-') {
        region = region.start + 1 .. region.endInclusive
        multiplier = -1
    }
    else {
        multiplier = 1
    }

    val first: Char = get(region.start)

    if (first.isDigit()) {
        val last_digit: Int = region.last { get(it).isDigit() }
        if (last_digit != -1) {
            substring(region.start, last_digit + 1).toIntOrNull()?.let { return it * multiplier }
        }

        if (first == '0') {
            when (getOrNull(region.start + 1)) {
                'x' -> {
                    @OptIn(ExperimentalStdlibApi::class)
                    return substring(region.start + 2, region.endInclusive + 1).trimEnd('u', 'U').hexToInt() * multiplier
                }
                'b' -> {
                    return substring(region.start + 2, region.endInclusive + 1).toInt(2) * multiplier
                }
            }
        }
    }
    else if (region.size == 3 && first == '\'' && get(region.endInclusive) == '\'') {
        return get(region.start + 1).code * multiplier
    }

    substring(region).also { string ->
        values[string]?.also { return it * multiplier }

        parser.getConstantExpressionValue(string)?.also { return it * multiplier }

        if (parser.getTypedef(string) != null) {
            return null
        }
    }

    // -----

    val seq: MutableList<Any> = mutableListOf()

    var i: Int = region.start
    var start: Int = i
    while (i <= region.endInclusive) {
        if (get(i) == '(') {
            var end: Int = i
            var depth: Int = 0
            while (true) {
                val c: Char = getOrNull(++end) ?: throw RuntimeException("Bracket at position $i not closed")
                if (c == '(') {
                    depth++
                }
                else if (c == ')' && depth-- <= 0) {
                    break
                }
            }

            val between: IntRange = start until i
            if (between.size > 0 && between.any { !get(it).isWhitespace() }) {
                seq.add(between)
            }

            seq.add(i + 1 until end)
            i = end + 1
            start = i
            continue
        }

        for (op in DoubleOperation.entries) {
            if (region.contains(i + op.kw.length - 1) && regionMatches(i, op.kw, 0, op.kw.length)) {
                val between: IntRange = start until i
                if (between.any { !get(it).isWhitespace() }) {
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

    val end: IntRange = start .. region.endInclusive
    if (end.any { !get(it).isWhitespace() }) {
        seq.add(end)
    }

    check(seq.isNotEmpty())

    try {
        var a: Int? = null
        var op: DoubleOperation? = null

        while (seq.isNotEmpty()) {
            var part: Any = seq.removeFirst()

            check(part != region) { "Part $part is the entire region" }

            if (part is IntRange) {
                val string: String = substring(part)
                val operation: SingleOperation? = SingleOperation.entries.firstOrNull { string == it.kw }
                if (operation != null) {
                    val value: Int = when (val first: Any = seq.removeFirst()) {
                        is Int -> first
                        is IntRange -> tryAllOperations(values, parser, depth + 1, first) ?: throw NullPointerException(substring(first))
                        else -> throw IllegalStateException("$first (${first::class})")
                    }

                    part = operation.perform(value)
                    if (seq.isEmpty()) {
                        return part
                    }
                }
            }

            if (a == null) {
                if (seq.isEmpty()) {
                    check(part is IntRange) { part }
                    return tryAllOperations(values, parser, depth + 1, part)
                }

                a = when (part) {
                    is Int -> part
                    is IntRange -> tryAllOperations(values, parser, depth + 1, part)
                    else -> throw IllegalStateException("$part (${part::class})")
                }
            }
            else if (op == null) {
                check(part is DoubleOperation)
                op = part
            }
            else {
                val b: Int = when (part) {
                    is Int -> part
                    is IntRange -> tryAllOperations(values, parser, depth + 1, part) ?: throw NullPointerException(substring(part))
                    else -> throw IllegalStateException("$part (${part::class})")
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
        throw RuntimeException("Parsing sequence failed ($seq, $this, $region)", e)
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

private val IntRange.size: Int get() = endInclusive - start + 1
