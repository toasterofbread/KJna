package dev.toastbits.kjna.c

import dev.toastbits.kjna.grammar.*

sealed interface CType {
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
        BOOL
    }

    data class TypeDef(val name: String): CType

    data class Struct(val name: String, val definition: CStructDefinition): CType

    data class Union(val values: Map<String, CValueType>): CType

    data class Enum(val name: String, val values: Map<String, Int>): CType

    data class Function(val shape: CFunctionDeclaration): CType
}

fun parseDeclarationSpecifierType(declaration_specifiers: List<CParser.DeclarationSpecifierContext>): CType {
    require(declaration_specifiers.isNotEmpty())

    val qualifiers: MutableList<CParser.TypeQualifierContext> = mutableListOf()
    val specifiers: MutableList<CParser.TypeSpecifierContext> = mutableListOf()

    for (specifier in declaration_specifiers) {
        specifier.typeQualifier()?.also { qualifiers.add(it) }
        specifier.typeSpecifier()?.also { specifiers.add(it) }
    }

    return parseType(specifiers, qualifiers)
}

fun parseType(specifiers: List<CParser.TypeSpecifierContext>, qualifiers: List<CParser.TypeQualifierContext> = emptyList()): CType {
    require(specifiers.isNotEmpty())

    val modifiers: List<CTypeModifier> = specifiers.dropLast(1).mapNotNull { parseModifierSpecifier(it) }
    var type: CType = parseTypeSpecifier(specifiers.last())

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

private fun parseTypeSpecifier(type_specifier: CParser.TypeSpecifierContext): CType {
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

    type_specifier.typedefName()?.Identifier()?.text?.also {
        return CType.TypeDef(it)
    }

    type_specifier.structOrUnionSpecifier()?.also { specifier ->
        val struct_or_union: CParser.StructOrUnionContext = specifier.structOrUnion()
        val struct_declarations: List<CParser.StructDeclarationContext> = specifier.structDeclarationList()?.structDeclaration().orEmpty()

        val struct_definition: CStructDefinition = parseStructDefinition(struct_declarations)

        if (struct_or_union.Struct() != null) {
            val name: String = specifier.Identifier()?.text ?: throw NullPointerException(type_specifier.text)
            return CType.Struct(name, struct_definition)
        }
        else if (struct_or_union.Union() != null) {
            check(specifier.Identifier()?.text == null) { type_specifier.text }
            return CType.Union(struct_definition.fields)
        }
        else {
            throw NotImplementedError(type_specifier.text)
        }
    }

    type_specifier.enumSpecifier()?.also { enum_specifier ->
        val name: String = enum_specifier.Identifier()?.text ?: throw NullPointerException(type_specifier.text)
        val values: MutableMap<String, Int> = mutableMapOf()

        var previous_value: Int = -1
        for (item in enum_specifier.enumeratorList()?.enumerator().orEmpty()) {
            val key: String  = item.enumerationConstant().Identifier().text
            val value: Int = item.constantExpression()?.text?.toInt() ?: (previous_value + 1)
            previous_value = value

            values[key] = value
        }

        return CType.Enum(name, values)
    }

    TODO(type_specifier.text)
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
