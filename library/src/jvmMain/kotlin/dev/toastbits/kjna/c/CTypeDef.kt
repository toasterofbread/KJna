package dev.toastbits.kjna.c

import dev.toastbits.kjna.grammar.*

data class CTypeDef(val name: String, val type: CValueType)

fun parseTypedefDeclaration(external_declaration: CParser.ExternalDeclarationContext): CTypeDef? {
    val specifiers: List<CParser.DeclarationSpecifierContext> =
        external_declaration.declaration()?.declarationSpecifiers()?.declarationSpecifier() ?: return null

    if (specifiers.isEmpty()) {
        return null
    }

    if (specifiers.size < 2) {
        TODO(external_declaration.text)
    }

    var name: String? = null
    var pointer_depth: Int = 0

    if (specifiers.first().storageClassSpecifier()?.Typedef() == null) {
        return null
    }

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

fun CType.TypeDef.resolve(typedefs: Map<String, CValueType>): CValueType {
    val passed: MutableList<String> = mutableListOf(name)
    while (true) {
        val type: CValueType = typedefs[passed.last()] ?: break
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

    throw RuntimeException("Unresolved typedef '$this'")
}
