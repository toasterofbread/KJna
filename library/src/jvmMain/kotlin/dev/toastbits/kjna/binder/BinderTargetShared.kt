package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.CFunctionDeclaration
import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CValueType
import dev.toastbits.kjna.c.resolve
import withIndex

class BinderTargetShared(): KJnaBinderTarget {
    override fun getClassModifiers(): List<String> = listOf("expect")

    override fun implementKotlinFunction(function: CFunctionDeclaration, function_header: String, context: BindingGenerator.GenerationScope): String {
        return function_header
    }

    override fun implementKotlinStructConstructor(struct: CType.Struct, context: BindingGenerator.GenerationScope): String? {
        return null
    }

    override fun implementKotlinStructField(name: String, index: Int, type: CValueType, type_name: String, struct: CType.Struct, context: BindingGenerator.GenerationScope): String {
        val actual_type: CValueType =
            if (type.type is CType.TypeDef) type.type.resolve(context.binder.typedefs)
            else type

        val field_type: String =
            if (actual_type.type is CType.Union) "val"
            else "var"

        return "$field_type $name: $type_name"
    }

    override fun implementKotlinUnionConstructor(union: CType.Union, name: String, context: BindingGenerator.GenerationScope): String? {
        return null
    }

    override fun implementKotlinUnionField(name: String, index: Int, type: CValueType, type_name: String, union: CType.Union, union_name: String, context: BindingGenerator.GenerationScope): String {
        val actual_type: CValueType =
            if (type.type is CType.TypeDef) type.type.resolve(context.binder.typedefs)
            else type

        val field_type: String =
            if (actual_type.type is CType.Union) "val"
            else "var"

        var ret: String = "$field_type $name: $type_name"
        if (ret.last() != '?') {
            ret += '?'
        }
        return ret
    }

    override fun getEnumFileContent(enm: CType.Enum, context: BindingGenerator.GenerationScope): String =
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
                appendLine()
            }

            append('}')
        }

    companion object {
        const val ENUM_CLASS_VALUE_PARAM: String = "value"
    }
}
