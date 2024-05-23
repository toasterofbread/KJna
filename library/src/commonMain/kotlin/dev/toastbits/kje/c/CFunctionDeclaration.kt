package dev.toastbits.kje.c

import dev.toastbits.kje.grammar.*

data class CFunctionDeclaration(
    val name: String,
    val return_type: CValueType?,
    val parameters: List<CFunctionParameter> = emptyList()
)

data class CFunctionParameter(
    val name: String?,
    val type: CValueType
)

fun parseFunctionDeclaration(external_declaration: CParser.ExternalDeclarationContext): CFunctionDeclaration? {
    val declarator: CParser.DeclaratorContext =
        external_declaration.declaration()?.initDeclaratorList()?.initDeclarator()?.firstOrNull()?.declarator() ?: return null

    val name: String = declarator.directDeclarator().directDeclarator()?.Identifier()?.text ?: return null

    val return_pointer_depth: Int = declarator.pointer()?.Star()?.size ?: 0
    val return_type: CType = external_declaration.declaration()?.declarationSpecifiers()?.declarationSpecifier()?.let { parseDeclarationSpecifierType(it) } ?: return null

    val declarator_parameters: List<CParser.ParameterDeclarationContext> =
        declarator.directDeclarator().parameterTypeList()?.parameterList()?.parameterDeclaration() ?: return null

    val parameters: List<CFunctionParameter> = declarator_parameters.mapNotNull { param ->
        val type_info = param.declarationSpecifiers()?.declarationSpecifier() ?: return@mapNotNull null

        val param_declarator: CParser.DeclaratorContext = param.declarator() ?: return@mapNotNull null
        val pointer_depth: Int = param_declarator.pointer()?.Star()?.size ?: 0
        val param_name: String? = param_declarator.directDeclarator().Identifier()?.text

        CFunctionParameter(
            name = param_name,
            type = CValueType(
                type = parseDeclarationSpecifierType(type_info),
                pointer_depth = pointer_depth
            )
        )
    }

    return CFunctionDeclaration(
        name = name,
        return_type = CValueType(return_type, pointer_depth = return_pointer_depth),
        parameters = parameters
    )
}
