package dev.toastbits.kjna.binder.target

import dev.toastbits.kjna.c.CFunctionDeclaration
import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CValueType
import dev.toastbits.kjna.c.resolve
import dev.toastbits.kjna.c.fullyResolve
import dev.toastbits.kjna.binder.BindingGenerator
import dev.toastbits.kjna.binder.KJnaBinder
import dev.toastbits.kjna.binder.Constants
import withIndex

class KJnaBindTargetShared(): KJnaBindTarget {
    override fun getClassModifiers(): List<String> = listOf("expect")

    override fun implementFunction(function: CFunctionDeclaration, function_header: String, header_class_name: String, context: BindingGenerator.GenerationScope): String {
        return function_header
    }

    override fun implementHeaderConstructor(context: BindingGenerator.GenerationScope): String {
        return "()"
    }

    override fun implementStructField(name: String, index: Int, type: CValueType, type_name: String, struct: CType.Struct, struct_name: String, scope_name: String?, context: BindingGenerator.GenerationScope): String {
        val actual_type: CValueType = type.fullyResolve(context.binder)

        val assignable: Boolean =
            actual_type.type !is CType.Union && ((type.pointer_depth + actual_type.pointer_depth) > 0 || actual_type.type !is CType.Struct)

        val field_type: String =
            if (assignable) "var"
            else "val"

        return "$field_type ${Constants.formatKotlinFieldName(name)}: $type_name"
    }

    override fun implementUnionConstructor(union: CType.Union, name: String, context: BindingGenerator.GenerationScope): String? {
        return null
    }

    override fun implementUnionField(name: String, index: Int, type: CValueType, type_name: String, union: CType.Union, union_name: String, union_field_name: String?, scope_name: String?, context: BindingGenerator.GenerationScope): String {
        val actual_type: CValueType = type.fullyResolve(context.binder)

        val field_type: String =
            if (actual_type.type is CType.Union) "val"
            else "var"

        var ret: String = "$field_type $name: $type_name"
        if (ret.last() != '?') {
            ret += '?'
        }
        return ret
    }

    override fun implementEnumFileContent(enm: CType.Enum, context: BindingGenerator.GenerationScope): String =
        buildString {
            append("enum class ")
            append(enm.name)
            appendLine("(val $ENUM_CLASS_VALUE_PARAM: Int) {")

            for ((index, name, value) in enm.values.withIndex()) {
                append("    ")
                append(name)
                append('(')
                append(value)
                append(')')

                if (index + 1 != enm.values.size) {
                    append(',')
                }
                else {
                    append(';')
                }
                appendLine()
            }

            appendLine()
            appendLine("    companion object")

            append('}')
        }

    companion object {
        const val ENUM_CLASS_VALUE_PARAM: String = "value"
    }
}
