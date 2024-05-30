package dev.toastbits.kjna.binder.target

import dev.toastbits.kjna.c.CFunctionDeclaration
import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CValueType
import dev.toastbits.kjna.c.resolve
import dev.toastbits.kjna.binder.BindingGenerator
import dev.toastbits.kjna.binder.KJnaBinder
import dev.toastbits.kjna.runtime.RuntimeType


class BinderTargetDisabled(): KJnaBinderTarget {
    override fun getClassModifiers(): List<String> = listOf("actual")

    override fun implementFunction(function: CFunctionDeclaration, function_header: String, header: KJnaBinder.Header, context: BindingGenerator.GenerationScope): String {
        return "actual $function_header { ${context.getThrowAccessError()} }"
    }

    override fun implementStructField(name: String, index: Int, type: CValueType, type_name: String, struct: CType.Struct, context: BindingGenerator.GenerationScope): String {
        val actual_type: CValueType =
            if (type.type is CType.TypeDef) type.type.resolve(context.binder.typedefs)
            else type

        val assignable: Boolean =
            actual_type.type !is CType.Union && ((type.pointer_depth + actual_type.pointer_depth) > 0 || actual_type.type !is CType.Struct)

        val field_type: String =
            if (assignable) "var"
            else "val"

        return buildString {
            appendLine("actual $field_type $name: $type_name")
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

    override fun implementUnionField(name: String, index: Int, type: CValueType, type_name: String, union: CType.Union, union_name: String, union_field_name: String?, scope_name: String, context: BindingGenerator.GenerationScope): String {
        val actual_type: CValueType =
            if (type.type is CType.TypeDef) type.type.resolve(context.binder.typedefs)
            else type

        val assignable: Boolean = actual_type.type !is CType.Union
        val field_type: String =
            if (assignable) "var"
            else "val"

        return buildString {
            append("actual $field_type $name: $type_name")
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

    private fun BindingGenerator.GenerationScope.getThrowAccessError(): String {
        val KJnaDisabledPackageAccessException: String = importRuntimeType(RuntimeType.KJnaDisabledPackageAccessException)
        return "throw $KJnaDisabledPackageAccessException()"
    }
}
