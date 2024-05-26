package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CValueType

fun generateStruct(struct: CType.Struct, target: KJnaBinderTarget, generator: BindingGenerator): String  =
    buildString {
        val body: String
        val unions: MutableList<String> = mutableListOf()

        val imports: List<Pair<String, String?>> =
            generator.generationScope {
                lateinit var createUnion: (CType.Union, String) -> String
                createUnion = { union, field ->
                    val union_name: String = getUnionTypeName(field)
                    val union_content: String? = generateUnion(union_name, union, target) { union, name -> createUnion(union, union_name + '_' + name) }
                    if (union_content != null) {
                        unions.add(union_content)
                    }
                    union_name
                }

                body = generateStructBody(struct, target, createUnion)
            }

        append(generateImportBlock(imports))

        append(body)

        if (unions.isNotEmpty()) {
            appendLine()
        }

        for (union in unions) {
            appendLine()
            append(union)
        }
    }

private fun BindingGenerator.GenerationScope.generateStructBody(struct: CType.Struct, target: KJnaBinderTarget, createUnion: (CType.Union, String) -> String): String =
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

            for ((name, type) in struct.definition.fields) {
                val type_name: String? = type.toKotlinTypeName(false) { createUnion(it, name) }
                if (type_name == null) {
                    throw NullPointerException(struct.toString())
                }
                appendLine(target.implementKotlinStructField(name, type, type_name, struct, this@generateStructBody).prependIndent("    "))
            }

            append("}")
        }
    }
