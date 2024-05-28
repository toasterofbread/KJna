package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CValueType
import dev.toastbits.kjna.binder.target.KJnaBinderTarget
import withIndex

fun BindingGenerator.GenerationScope.generateStructBody(struct: CType.Struct, target: KJnaBinderTarget): String =
    buildString {
        for (modifier in target.getClassModifiers()) {
            append(modifier)
            append(' ')
        }
        append("class ")
        append(struct.name)

        val struct_constructor: String? = target.implementStructConstructor(struct, this@generateStructBody)
        if (struct_constructor != null) {
            append(' ')
            append(struct_constructor)
        }

        val companion_object: String? = target.implementStructCompanionObject(struct, this@generateStructBody)

        if (struct.definition.fields.isNotEmpty() || companion_object != null) {
            appendLine(" {")

            for ((index, name, type) in struct.definition.fields.withIndex()) {
                val type_name: String? = type.toKotlinTypeName(false) { createUnion(struct.name, index, it) }
                if (type_name == null) {
                    throw NullPointerException(struct.toString())
                }
                appendLine(target.implementStructField(name, index, type, type_name, struct, this@generateStructBody).prependIndent("    "))
            }

            val to_string: String? = target.implementStructToStringMethod(struct, this@generateStructBody)
            if (to_string != null) {
                if (struct.definition.fields.isNotEmpty()) {
                    appendLine()
                }
                appendLine(to_string.prependIndent("    "))
            }

            if (companion_object != null) {
                if (struct.definition.fields.isNotEmpty() || to_string != null) {
                    appendLine()
                }

                appendLine(companion_object.prependIndent("    "))
            }

            append("}")
        }
    }
