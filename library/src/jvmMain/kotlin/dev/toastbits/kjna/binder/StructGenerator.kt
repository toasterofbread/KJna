package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CValueType
import dev.toastbits.kjna.binder.target.KJnaBindTarget
import withIndex

fun BindingGenerator.GenerationScope.generateStructBody(name: String, struct: CType.Struct, target: KJnaBindTarget, scope_name: String? = null, includeField: ((String, CType) -> Boolean)? = null): String =
    buildString {
        val struct_annotation: String? = target.implementStructAnnotation(struct, this@generateStructBody)
        if (struct_annotation != null) {
            appendLine(struct_annotation)
        }

        for (modifier in target.getClassModifiers()) {
            append(modifier)
            append(' ')
        }
        append("class ")
        append(name)

        val struct_constructor: String? = target.implementStructConstructor(struct, name, this@generateStructBody)
        if (struct_constructor != null) {
            append(' ')
            append(struct_constructor)
        }

        val companion_object: String? = target.implementStructCompanionObject(struct, this@generateStructBody)

        if (struct.definition?.fields?.isNotEmpty() == true || companion_object != null) {
            appendLine(" {")

            for ((index, name, type) in struct.definition?.fields?.withIndex() ?: emptyList()) {
                if (includeField?.invoke(name, type.type) == false) {
                    continue
                }

                val type_name: String? = 
                    type.toKotlinTypeName(
                        false,
                        createUnion = { createUnion(struct.name!!, name, index, it) },
                        createStruct = { createStruct(struct.name!!, name, index, it) }
                    ) 
                
                if (type_name == null) {
                    throw NullPointerException(struct.toString())
                }
                appendLine(target.implementStructField(name, index, type, type_name, struct, scope_name ?: name, this@generateStructBody).prependIndent("    "))
            }

            val to_string: String? = target.implementStructToStringMethod(struct, this@generateStructBody)
            if (to_string != null) {
                if (struct.definition?.fields?.isNotEmpty() == true) {
                    appendLine()
                }
                appendLine(to_string.prependIndent("    "))
            }

            if (companion_object != null) {
                if (struct.definition?.fields?.isNotEmpty() == true || to_string != null) {
                    appendLine()
                }

                appendLine(companion_object.prependIndent("    "))
            }

            append("}")
        }
    }
