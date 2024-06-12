package dev.toastbits.kjna.binder.target

import dev.toastbits.kjna.c.CFunctionDeclaration
import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CValueType
import dev.toastbits.kjna.c.resolve
import dev.toastbits.kjna.c.fullyResolve
import dev.toastbits.kjna.binder.BindingGenerator
import dev.toastbits.kjna.binder.KJnaBinder
import dev.toastbits.kjna.binder.Constants
import dev.toastbits.kjna.runtime.RuntimeType

class KJnaBindTargetDisabled(private val base_target: KJnaBindTarget): KJnaBindTarget {
    override fun getClassModifiers(): List<String> = listOf("actual")

    override fun implementFunction(function: CFunctionDeclaration, function_header: String, header_class_name: String, context: BindingGenerator.GenerationScope): String {
        return "actual $function_header { ${context.getThrowAccessError()} }"
    }

    override fun implementStructField(name: String, index: Int, type: CValueType, type_name: String, struct: CType.Struct, struct_name: String, scope_name: String?, context: BindingGenerator.GenerationScope): String {
        val actual_type: CValueType = type.fullyResolve(context.binder)

        val assignable: Boolean =
            actual_type.type !is CType.Union && (actual_type.pointer_depth > 0 || actual_type.type !is CType.Struct)

        val field_type: String =
            if (assignable) "var"
            else "val"

        return buildString {
            appendLine("actual $field_type ${Constants.formatKotlinFieldName(name)}: $type_name")
            append("    get() { ${context.getThrowAccessError()} }")

            if (assignable) {
                appendLine()
                append("    set(value) { ${context.getThrowAccessError()} }")
            }
        }
    }

    override fun implementUnionConstructor(union: CType.Union, name: String, context: BindingGenerator.GenerationScope): String? {
        return null
    }

    override fun implementUnionField(name: String, index: Int, type: CValueType, type_name: String, union: CType.Union, union_name: String, union_field_name: String?, scope_name: String?, context: BindingGenerator.GenerationScope): String {
        val actual_type: CValueType = type.fullyResolve(context.binder)

        val assignable: Boolean = actual_type.type !is CType.Union
        val field_type: String =
            if (assignable) "var"
            else "val"

        return buildString {
            append("actual $field_type ${Constants.formatKotlinFieldName(name)}: $type_name")
            if (type_name.last() != '?') {
                append('?')
            }
            appendLine()
            append("    get() { ${context.getThrowAccessError()} }")

            if (assignable) {
                appendLine()
                append("    set(value) { ${context.getThrowAccessError()} }")
            }
        }
    }

    override fun implementEnumFileContent(enm: CType.Enum, context: BindingGenerator.GenerationScope): String? =
        base_target.implementEnumFileContent(enm, context)

    private fun BindingGenerator.GenerationScope.getThrowAccessError(): String {
        val KJnaDisabledPackageAccessException: String = importRuntimeType(RuntimeType.KJnaDisabledPackageAccessException)
        return "throw $KJnaDisabledPackageAccessException()"
    }
}
