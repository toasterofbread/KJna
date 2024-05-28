package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.binder.target.KJnaBinderTarget
import withIndex

fun BindingGenerator.GenerationScope.generateUnion(name: String, union: CType.Union, union_field_name: String?, scope_name: String, target: KJnaBinderTarget): String =
    buildString {
        for (modifier in target.getClassModifiers()) {
            append(modifier)
            append(' ')
        }
        append("class ")
        append(name)

        val union_constructor: String? = target.implementUnionConstructor(union, name, this@generateUnion)
        if (union_constructor != null) {
            append(' ')
            append(union_constructor)
        }

        if (union.values.isNotEmpty()) {
            appendLine(" {")

            for ((index, field, type) in union.values.withIndex()) {
                val type_name: String? = type.toKotlinTypeName(false) { createUnion(name, field, index, it) }
                if (type_name == null) {
                    throw NullPointerException(union.toString())
                }
                appendLine(target.implementUnionField(field, index, type, type_name, union, name, union_field_name, scope_name, this@generateUnion).prependIndent("    "))
            }

            append("}")
        }
    }
