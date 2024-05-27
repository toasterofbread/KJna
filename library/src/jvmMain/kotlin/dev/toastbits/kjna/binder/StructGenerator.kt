package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CValueType
import withIndex

fun BindingGenerator.GenerationScope.generateStructBody(struct: CType.Struct, target: KJnaBinderTarget): String =
    buildString {
        for (modifier in target.getClassModifiers()) {
            append(modifier)
            append(' ')
        }
        append("class ")
        append(struct.name)

        val struct_constructor: String? = target.implementKotlinStructConstructor(struct, this@generateStructBody)
        if (struct_constructor != null) {
            append(' ')
            append(struct_constructor)
        }

        if (struct.definition.fields.isNotEmpty()) {
            appendLine(" {")

            for ((index, name, type) in struct.definition.fields.withIndex()) {
                val type_name: String? = type.toKotlinTypeName(false) { createUnion(struct.name, index, it) }
                if (type_name == null) {
                    throw NullPointerException(struct.toString())
                }
                appendLine(target.implementKotlinStructField(name, index, type, type_name, struct, this@generateStructBody).prependIndent("    "))
            }

            val companion_object: String? = target.getStructCompanionObject(struct, this@generateStructBody)
            if (companion_object != null) {
                appendLine()
                appendLine(companion_object.prependIndent("    "))
            }

            append("}")
        }
    }
