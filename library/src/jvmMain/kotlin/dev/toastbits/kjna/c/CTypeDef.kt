package dev.toastbits.kjna.c

import dev.toastbits.kjna.grammar.*

data class CTypeDef(val name: String, val type: CValueType)

fun PackageGenerationScope.parseTypedefDeclaration(external_declaration: CParser.ExternalDeclarationContext): CTypeDef? {
    var specifiers: List<CParser.DeclarationSpecifierContext> =
        external_declaration.declaration()?.declarationSpecifiers()?.declarationSpecifier() ?: return null

    if (specifiers.isEmpty()) {
        return null
    }

    if (specifiers.size == 1) {
        val struct_or_union: CParser.StructOrUnionSpecifierContext = specifiers.first().typeSpecifier()?.structOrUnionSpecifier() ?: return null
        if (struct_or_union.structOrUnion()?.Struct() != null) {
            val name: String = struct_or_union.Identifier()?.text ?: return null
            val type: CType = CType.Struct(name, CStructDefinition(emptyMap()))

            return CTypeDef(name!!, CValueType(type, 0))
        }
        else if (struct_or_union.structOrUnion()?.Union() != null) {
            TODO(specifiers.first().text)
        }
        else {
            TODO(specifiers.first().text)
        }
    }

    var name: String? = null
    var pointer_depth: Int = 0

    val typedef_start: Int = specifiers.indexOfFirst { it.storageClassSpecifier()?.Typedef() != null }
    if (typedef_start == -1) {
        return null
    }
    specifiers = specifiers.drop(typedef_start)

    if (specifiers.size < 3) {
        for (declarator in external_declaration.declaration()?.initDeclaratorList()?.initDeclarator()?.map { it.declarator() }.orEmpty()) {
            declarator.directDeclarator().Identifier()?.text?.also {
                name = it
                pointer_depth = declarator.pointer()?.Star()?.size ?: 0
            }

            if (name != null) {
                break
            }
        }
    }

    val type: CType

    if (name == null) {
        name = specifiers.last().typeSpecifier()?.typedefName()?.Identifier()?.text ?: return null
        type = parseDeclarationSpecifierType(specifiers.drop(1).dropLast(1))
    }
    else {
        type = parseDeclarationSpecifierType(specifiers.drop(1))
    }

    return CTypeDef(name!!, CValueType(type, pointer_depth = pointer_depth))
}

fun CType.TypeDef.resolve(typedefs: Map<String, CTypeDef>): CValueType {
    val passed: MutableList<String> = mutableListOf(name)
    while (true) {
        val type: CValueType = typedefs[passed.last()]?.type ?: break
        if (type.type is CType.TypeDef) {
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
    }

    throw RuntimeException("Unresolved typedef '$this' -> '${passed.last()}' (${passed.size})")
}
