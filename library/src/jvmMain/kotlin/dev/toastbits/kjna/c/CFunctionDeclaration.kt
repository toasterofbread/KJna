package dev.toastbits.kjna.c

import dev.toastbits.kjna.grammar.*
import dev.toastbits.kjna.c.PackageGenerationScope

data class CFunctionDeclaration(
    val name: String,
    val return_type: CValueType?,
    val parameters: List<CFunctionParameter> = emptyList()
)

private fun PackageGenerationScope.parseFunctionParameter(param: CParser.ParameterDeclarationContext): CFunctionParameter {
    val param_declarator: CParser.DeclaratorContext = param.declarator() ?: return CFunctionParameter(null, CValueType(CType.Primitive.VOID, 0))
    val direct_declarator: CParser.DirectDeclaratorContext = param_declarator.directDeclarator()

    val type: CType =
        param.declarationSpecifiers()?.declarationSpecifier()?.let { parseDeclarationSpecifierType(it) }
        ?: throw RuntimeException("No type in ${param.text}")

    val type_pointer_depth: Int = param_declarator.pointer()?.Star()?.size ?: 0

    val callback_declarator: CParser.DeclaratorContext? = direct_declarator.directDeclarator()?.declarator()
    if (callback_declarator != null) {
        val param_name: String? = callback_declarator.directDeclarator().Identifier()?.text
        val pointer_depth: Int = callback_declarator.pointer()?.Star()?.size ?: 0

        val callback_params: List<CFunctionParameter> = direct_declarator.parameterTypeList()?.parameterList()?.parameterDeclaration()?.map { parseFunctionParameter(it) } ?: throw RuntimeException(param.text)

        return CFunctionParameter(
            name = param_name,
            type = CValueType(
                type = CType.Function(
                    CFunctionDeclaration(
                        name = param_name ?: "",
                        return_type = CValueType(type, type_pointer_depth),
                        parameters = callback_params
                    )
                ),
                pointer_depth = pointer_depth
            )
        )
    }

    val param_name: String? = direct_declarator.Identifier()?.text
    return CFunctionParameter(
        name = param_name,
        type = CValueType(
            type = type,
            pointer_depth = type_pointer_depth
        )
    )
}

fun PackageGenerationScope.parseFunctionDeclaration(external_declaration: CParser.ExternalDeclarationContext): CFunctionDeclaration? {
    try {
        val declarator: CParser.DeclaratorContext =
            external_declaration.declaration()?.initDeclaratorList()?.initDeclarator()?.firstOrNull()?.declarator() ?: return null

        val name: String = declarator.directDeclarator().directDeclarator()?.Identifier()?.text ?: return null

        val return_pointer_depth: Int = declarator.pointer()?.Star()?.size ?: 0
        val return_type: CType = external_declaration.declaration()?.declarationSpecifiers()?.declarationSpecifier()?.let { parseDeclarationSpecifierType(it) } ?: return null

        val declarator_parameters: List<CParser.ParameterDeclarationContext> =
            declarator.directDeclarator().parameterTypeList()?.parameterList()?.parameterDeclaration() ?: return null

        val parameters: List<CFunctionParameter> = declarator_parameters.map { parseFunctionParameter(it) }

        return CFunctionDeclaration(
            name = name,
            return_type = CValueType(return_type, pointer_depth = return_pointer_depth),
            parameters = parameters
        )
    }
    catch (e: Throwable) {
        throw RuntimeException(external_declaration.text, e)
    }
}
