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

internal fun PackageGenerationScope.parseFunctionDeclaration(
    declaration: CParser.DeclarationContext
): CFunctionDeclaration? {
    try {
        val declarator: CParser.DeclaratorContext =
            declaration.initDeclaratorList()?.initDeclarator()?.firstOrNull()?.declarator() ?: return null

        val name: String = declarator.directDeclarator().directDeclarator()?.Identifier()?.text ?: return null

        val return_pointer_depth: Int = declarator.pointer()?.Star()?.size ?: 0
        val return_type: CType = declaration.declarationSpecifiers()?.declarationSpecifier()?.let { parseDeclarationSpecifierType(it) } ?: return null

        val parameter_type_list: CParser.ParameterTypeListContext? = declarator.directDeclarator().parameterTypeList()

        val declarator_parameters: List<CParser.ParameterDeclarationContext> =
            parameter_type_list?.parameterList()?.parameterDeclaration() ?: return null

        val parameters: List<CFunctionParameter> = parseFunctionParameters(declarator_parameters)

        return CFunctionDeclaration(
            name = name,
            return_type = CValueType(return_type, pointer_depth = return_pointer_depth),
            parameters =
                if (parameter_type_list?.Ellipsis() == null) parameters
                else parameters + listOf(CFunctionParameter(null, CValueType(CType.Primitive.VALIST, 0)))
        )
    }
    catch (e: Throwable) {
        throw RuntimeException(declaration.text, e)
    }
}

internal fun PackageGenerationScope.parseFunctionParameters(parameters: List<CParser.ParameterDeclarationContext>): List<CFunctionParameter> {
    return processFunctionParameters(parameters.map { parseFunctionParameter(it) }) { it.fullyResolve(parser) }
}

private fun processFunctionParameters(parameters: List<CFunctionParameter>, resolveType: (CValueType) -> CValueType): List<CFunctionParameter> {
    val function_params: MutableList<CFunctionParameter> = parameters.toMutableList()

    // val i: MutableListIterator<CFunctionParameter> = function_params.listIterator()
    // while (i.hasNext()) {
    //     val param: CFunctionParameter = i.next()

    //     val param_type: CValueType = resolveType(param.type)
    //     if (param_type.type !is CType.Function) {
    //         continue
    //     }

    //     if (param_type.pointer_depth != 1) {
    //         continue
    //     }

    //     val send_param_index: Int

    //     if (function_params.getOrNull(i.nextIndex())?.type?.let { resolveType(it) } == CType.Function.FUNCTION_DATA_PARAM_TYPE) {
    //         send_param_index = i.nextIndex()
    //     }
    //     else if (function_params.getOrNull(i.previousIndex() - 1)?.type?.let { resolveType(it) } == CType.Function.FUNCTION_DATA_PARAM_TYPE) {
    //         send_param_index = i.previousIndex() - 1
    //     }
    //     else {
    //         continue
    //     }

    //     val func: CType.Function = param_type.type
    //     if (func.shape.parameters.size != 1) {
    //         continue
    //     }

    //     if (func.shape.parameters.single().type != CType.Function.FUNCTION_DATA_PARAM_TYPE) {
    //         continue
    //     }

    //     i.set(param.copy(
    //         type = param_type.copy(
    //             type = func.copy(
    //                 data_params = CType.Function.DataParams(
    //                     send = send_param_index,
    //                     recv = 0
    //                 ),
    //                 shape = func.shape.copy(parameters = emptyList())
    //             )
    //         )
    //     ))
    // }

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
