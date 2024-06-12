package dev.toastbits.kjna.c

import dev.toastbits.kjna.grammar.*
import dev.toastbits.kjna.binder.KJnaBinder

data class CTypedef(val name: String, val type: CValueType)

internal fun PackageGenerationScope.parseTypedefDeclaration(external_declaration: CParser.ExternalDeclarationContext): CTypedef? {
    var specifiers: List<CParser.DeclarationSpecifierContext> =
        external_declaration.declaration()?.declarationSpecifiers()?.declarationSpecifier() ?: return null

    if (specifiers.isEmpty()) {
        return null
    }

    if (specifiers.size == 1) {
        val struct_or_union: CParser.StructOrUnionSpecifierContext = specifiers.single().typeSpecifier()?.structOrUnionSpecifier() ?: return null
        val name: String = struct_or_union.Identifier()?.text ?: return null
        val struct_definition: CStructDefinition? = struct_or_union.structDeclarationList()?.structDeclaration()?.let { parseStructDefinition(it) }

        val type: CType
        if (struct_or_union.structOrUnion()?.Struct() != null) {
            type = CType.Struct(name, struct_definition, null)
        }
        else if (struct_or_union.structOrUnion()?.Union() != null) {
            type = CType.Union(name, struct_definition?.fields, null)
        }
        else {
            TODO(specifiers.first().text)
        }

        return CTypedef(name, CValueType(type, 0))
    }

    val typedef_start: Int = specifiers.indexOfFirst { it.storageClassSpecifier()?.Typedef() != null }
    if (typedef_start == -1) {
        return null
    }
    specifiers = specifiers.drop(typedef_start)

    parseFunctionTypedefDeclaration(specifiers, external_declaration)?.also { return it }

    var name: String? = null
    var pointer_depth: Int = 0
    var array_size: Int? = null

    for (declarator in external_declaration.declaration()?.initDeclaratorList()?.initDeclarator()?.map { it.declarator() }.orEmpty()) {
        val direct_declarator: CParser.DirectDeclaratorContext = declarator.directDeclarator()

        if (direct_declarator.LeftBracket() != null) {
            check(direct_declarator.RightBracket() != null)
            name = declarator.directDeclarator().directDeclarator()!!.text
            array_size = direct_declarator.assignmentExpression()!!.conditionalExpression()!!.logicalOrExpression()!!.logicalAndExpression()!!.single().text.toInt()
            break
        }

        (direct_declarator.Identifier() ?: direct_declarator.directDeclarator()?.Identifier())?.text?.also {
            name = it
            pointer_depth = declarator.pointer()?.Star()?.size ?: 0
        }

        if (name != null) {
            break
        }
    }

    val type: CType

    try {
        if (name == null) {
            name = specifiers.last().typeSpecifier()?.typedefName()?.Identifier()?.text ?: return null
            type = parseDeclarationSpecifierType(specifiers.drop(1).dropLast(1), name)
        }
        else {
            type = parseDeclarationSpecifierType(specifiers.drop(1), name)
        }
    }
    catch (e: Throwable) {
        throw RuntimeException("parseDeclarationSpecifierType failed ($array_size, ${external_declaration.text}, ${specifiers.map { it.text }})", e)
    }

    if (array_size != null) {
        check(array_size > 0) { array_size }
        return CTypedef(name!!, CValueType(CType.Array(CValueType(type, pointer_depth = pointer_depth), array_size), 0))
    }

    return CTypedef(name!!, CValueType(type, pointer_depth = pointer_depth))
}

fun CType.Typedef.resolve(getTypedef: (String) -> CTypedef?): CValueType {
    val passed: MutableList<String> = mutableListOf(name)
    while (true) {
        val type: CValueType = getTypedef(passed.last())?.type ?: break
        if (type.type is CType.Typedef) {
            if (passed.contains(type.type.name)) {
                throw RuntimeException("Recursive typedef (this=$name, duplicate=${type.type.name}, passed=$passed}")
            }

            passed.add(type.type.name)
        }
        else {
            return type
        }
    }

    when (passed.last()) {
        "size_t" -> return CValueType(CType.Primitive.U_LONG, 0)
        "wchar_t" -> return CValueType(CType.Primitive.CHAR, 0)
        "__builtin_va_list" -> return CValueType(CType.Primitive.VALIST, 0)
    }

    throw RuntimeException("Unresolved typedef '$this' -> '${passed.last()}' (${passed.size})")
}

fun CValueType.fullyResolve(binder: KJnaBinder, ignore_typedef_overrides: Boolean = false): CValueType {
    if (type !is CType.Typedef) {
        return this
    }
    return type.resolve { binder.getTypedef(it, ignore_typedef_overrides = ignore_typedef_overrides) }.let { it.copy(pointer_depth = it.pointer_depth + pointer_depth) }
}

fun CValueType.fullyResolve(parser: CHeaderParser): CValueType {
    if (type !is CType.Typedef) {
        return this
    }
    return type.resolve { parser.getTypedef(it) }.let { it.copy(pointer_depth = it.pointer_depth + pointer_depth) }
}

private fun PackageGenerationScope.parseFunctionTypedefDeclaration(specifiers: List<CParser.DeclarationSpecifierContext>, external_declaration: CParser.ExternalDeclarationContext): CTypedef? {
    if (specifiers.size < 2) {
        return null
    }

    val init_declarators: List<CParser.InitDeclaratorContext>? = external_declaration.declaration()?.initDeclaratorList()?.initDeclarator()
    if (init_declarators?.size != 1) {
        return null
    }

    val direct_declarator: CParser.DirectDeclaratorContext = init_declarators?.firstOrNull()?.declarator()?.directDeclarator() ?: return null
    val directer_declarator: CParser.DirectDeclaratorContext = direct_declarator.directDeclarator() ?: return null

    val name: String = directer_declarator.declarator()?.directDeclarator()?.Identifier()?.text ?: return null
    val pointer_depth: Int = directer_declarator.declarator()?.pointer()?.Star()?.size ?: 0

    val declarator_parameters: List<CParser.ParameterDeclarationContext> =
        direct_declarator.parameterTypeList()?.parameterList()?.parameterDeclaration() ?: throw NullPointerException("No params")
    val parameters: List<CFunctionParameter> = declarator_parameters.let { parseFunctionParameters(it) }

    val return_type: CType = parseDeclarationSpecifierType(specifiers)
    val return_type_pointer_depth: Int = init_declarators.first().declarator()?.pointer()?.Star()?.size ?: 0

    return CTypedef(
        name,
        CValueType(
            CType.Function(
                CFunctionDeclaration(
                    name = name,
                    return_type = CValueType(return_type, return_type_pointer_depth),
                    parameters = parameters
                )
            ),
            pointer_depth
        )
    )
}
