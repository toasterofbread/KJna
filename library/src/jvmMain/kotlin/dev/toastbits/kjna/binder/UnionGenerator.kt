package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.CType

fun BindingGenerator.GenerationScope.generateUnion(name: String, union: CType.Union, target: KJnaBinderTarget, createUnion: (CType.Union, String) -> String): String? =
    buildString {
        for (modifier in target.getClassModifiers()) {
            append(modifier)
            append(' ')
        }
        append("class ")
        append(name)

        val union_constructor: String? = target.implementKotlinUnionConstructor(union, name, this@generateUnion)
        if (union_constructor != null) {
            append(' ')
            append(union_constructor)
        }

        if (union.values.isNotEmpty()) {
            appendLine(" {")

            for ((field, type) in union.values) {
                val type_name: String? = type.toKotlinTypeName(false) { createUnion(it, name + '_' + field) }
                if (type_name == null) {
                    throw NullPointerException(union.toString())
                }
                appendLine(target.implementKotlinUnionField(field, type, type_name, union, this@generateUnion).prependIndent("    "))
            }

            append("}")
        }
    }

    // if (target !is BinderTargetShared) {
    //     return null
    // }

    // return buildString {
    //     appendLine("data class $name(")

    //     for ((index, field, type) in union.values.withIndex()) {
    //         val type_name: String? = type.toKotlinTypeName(false, { createUnion(it, name) })
    //         if (type_name == null) {
    //             throw NullPointerException(union.toString())
    //         }

    //         append("    var $field: $type_name")
    //         if (!type_name.endsWith("?")) {
    //             append("?")
    //         }
    //         append(" = null")

    //         if (index + 1 != union.values.size) {
    //             append(",")
    //         }
    //         appendLine()
    //     }

    //     append(")")
    // }

fun getUnionTypeName(field_name: String): String =
    "union_$field_name"

fun <K, V> Map<K, V>.withIndex(): List<Triple<Int, K, V>> =
    entries.withIndex().map { Triple(it.index, it.value.key, it.value.value) }
