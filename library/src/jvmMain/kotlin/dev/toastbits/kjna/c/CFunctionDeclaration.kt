package dev.toastbits.kjna.c

import dev.toastbits.kjna.grammar.*
import dev.toastbits.kjna.c.PackageGenerationScope
import kotlinx.serialization.Serializable

@Serializable
data class CFunctionDeclaration(
    val name: String,
    val return_type: CValueType?,
    val parameters: List<CFunctionParameter> = emptyList()
)

private fun PackageGenerationScope.parseFunctionParameters(parameters: List<CParser.ParameterDeclarationContext>): List<CFunctionParameter> {
    val function_params: MutableList<CFunctionParameter> = parameters.map { parseFunctionParameter(it) }.toMutableList()

    val i: MutableListIterator<CFunctionParameter> = function_params.listIterator()
    while (i.hasNext()) {
        val param: CFunctionParameter = i.next()
        if (param.type.type !is CType.Function) {
            continue
        }

        if (param.type.pointer_depth != 1) {
            i.remove()
            continue
        }

        val next: CFunctionParameter? = function_params.getOrNull(i.nextIndex())
        if (next?.type != CType.Function.FUNCTION_DATA_PARAM_TYPE) {
            i.remove()
            continue
        }

        val func: CType.Function = param.type.type
        if (func.shape.parameters.size != 1) {
            i.remove()
            continue
        }

        if (func.shape.parameters.single().type != CType.Function.FUNCTION_DATA_PARAM_TYPE) {
            i.remove()
            continue
        }

        i.set(param.copy(
            type = param.type.copy(
                type = func.copy(
                    data_send_param = i.nextIndex(),
                    data_recv_param = 0,
                    shape = func.shape.copy(parameters = emptyList())
                )
            )
        ))
    }

    check(function_params.all { param ->
        if (param.type.type is CType.Function) param.type.type.data_send_param != null && param.type.type.data_recv_param != null
        else true
    })

    return function_params
}

private fun PackageGenerationScope.parseFunctionParameter(param: CParser.ParameterDeclarationContext): CFunctionParameter {
    val param_declarator: CParser.DeclaratorContext = param.declarator() ?: return CFunctionParameter(null, CValueType(CType.Primitive.VOID, 0))
    val direct_declarator: CParser.DirectDeclaratorContext = param_declarator.directDeclarator()

    val type: CType =
        param.declarationSpecifiers()?.declarationSpecifier()?.let { parseDeclarationSpecifierType(it) }
        ?: throw RuntimeException("No type in ${param.text}")

    val type_pointer_depth: Int = param_declarator.pointer()?.Star()?.size ?: 0

    val function_declarator: CParser.DeclaratorContext? = direct_declarator.directDeclarator()?.declarator()
    if (function_declarator != null) {
        val param_name: String? = function_declarator.directDeclarator().Identifier()?.text
        val pointer_depth: Int = function_declarator.pointer()?.Star()?.size ?: 0

        val function_params: List<CFunctionParameter> = direct_declarator.parameterTypeList()?.parameterList()?.parameterDeclaration()?.let { parseFunctionParameters(it) } ?: throw RuntimeException(param.text)

        return CFunctionParameter(
            name = param_name,
            type = CValueType(
                type = CType.Function(
                    CFunctionDeclaration(
                        name = param_name ?: "",
                        return_type = CValueType(type, type_pointer_depth),
                        parameters = function_params
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

        val parameters: List<CFunctionParameter> = declarator_parameters.let { parseFunctionParameters(it) }

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
