package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.CHeaderParser
import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CTypedef
import dev.toastbits.kjna.c.CStructDefinition
import dev.toastbits.kjna.c.CValueType
import dev.toastbits.kjna.binder.target.KJnaBindTarget

open class KJnaBinder(
    val package_name: String,
    val package_info: CHeaderParser.PackageInfo,
    val headers: List<Header>,
    val typedefs: MutableMap<String, CTypedef>,
    val anonymous_struct_indices: Map<Int, Int> = emptyMap()
) {
    open fun shouldIncludeStructField(name: String, type: CType, struct: CType.Struct): Boolean = true

    data class Header(
        val absolute_path: String,
        val class_name: String
    )

    data class BindingFile(
        val class_name: String,
        val content: String
    )

    data class GeneratedBindings(
        val top_level_package: String,
        val files: List<List<BindingFile>>
    )

    fun generateBindings(targets: Collection<KJnaBindTarget>): GeneratedBindings {
        val files: MutableList<MutableList<BindingFile>> = targets.map { mutableListOf<BindingFile>() }.toMutableList()

        val used_structs: MutableList<String> = package_info.structs.keys.toMutableList()
        val used_enums: MutableList<String> = mutableListOf()
        val used_unions: MutableList<String> = mutableListOf()

        val new_structs: MutableList<String> = used_structs.toMutableList()
        val new_unions: MutableList<String> = used_unions.toMutableList()
        var import_finished: Boolean = false

        val generator: BindingGenerator =
            BindingGenerator(
                this,
                anonymous_struct_indices = anonymous_struct_indices,
                getStructImport = { struct_name ->
                    if (!used_structs.contains(struct_name)) {
                        check(!import_finished) { struct_name }
                        used_structs.add(struct_name)
                        new_structs.add(struct_name)
                    }
                    return@BindingGenerator Constants.STRUCT_PACKAGE_NAME + '.' + struct_name
                },
                getUnionImport = { union_name ->
                    if (!used_unions.contains(union_name)) {
                        check(!import_finished) { union_name }
                        used_unions.add(union_name)
                        new_unions.add(union_name)
                    }
                    return@BindingGenerator Constants.UNION_PACKAGE_NAME + '.' + union_name
                },
                getEnumImport = { enum_name ->
                    if (!used_enums.contains(enum_name)) {
                        check(!import_finished) { enum_name }
                        used_enums.add(enum_name)
                    }
                    return@BindingGenerator Constants.ENUM_PACKAGE_NAME + '.' + enum_name
                }
            )

        val undefined_structs: MutableList<Int> = mutableListOf()

        // Gather references before final file generation
        for (target in targets) {
            generator.unhandledGenerationScope(target) {
                do {
                    val all_structs: List<CType.Struct> = used_structs.mapNotNull { typedefs[it]?.type?.type as? CType.Struct }
                    for (header in headers) {
                        generateHeaderFileContent(header, all_structs)
                    }

                    var i: Int = 0
                    while (i < new_structs.size) {
                        val struct_name: String = new_structs[i++]
                        val typedef: CTypedef? = typedefs[struct_name]
                        var is_undefined: Boolean = typedef == null

                        val struct: CType.Struct
                        if (typedef != null) {
                            struct = typedef.type.type as CType.Struct
                        }
                        else {
                            println("Warning: Creating dummy implementation for referenced but undefined struct '$struct_name'")
                            struct = CType.Struct(struct_name, null, null)
                            typedefs[struct_name] = CTypedef(struct_name, CValueType(struct, 0))
                            undefined_structs.add(i)
                        }

                        generateStructBody(struct_name, struct, target, includeField = { name, type -> shouldIncludeStructField(name, type, struct) })
                    }
                    new_structs.clear()

                    i = 0
                    while (i < new_unions.size) {
                        val union_name: String = new_unions[i++]
                        val union: CType.Union = typedefs[union_name]!!.type.type as CType.Union

                        generateUnion(union_name, union, null, union_name, target)
                    }
                    new_unions.clear()
                }
                while (new_structs.isNotEmpty())
            }
        }

        import_finished = true

        for ((i, struct_name) in used_structs.withIndex()) {
            val struct: CType.Struct = typedefs[struct_name]!!.type.type as CType.Struct
            if (struct.name != struct_name && typedefs.contains(struct.name)) {
                continue
            }

            val is_undefined: Boolean = undefined_structs.contains(i)

            for ((target_index, target) in targets.withIndex()) {
                generator.buildKotlinFile(target, Constants.STRUCT_PACKAGE_NAME) {
                    if (is_undefined) {
                        appendLine("// This is a dummy implementation generated for an unresolved struct")
                    }

                    append(generateStructBody(struct_name, struct, target, includeField = { name, type -> shouldIncludeStructField(name, type, struct) }))

                    files[target_index].add(BindingFile(Constants.STRUCT_PACKAGE_NAME + '.' + struct_name, build()))
                }
            }
        }

        for ((target_index, target) in targets.withIndex()) {
            for (union_name in used_unions) {
                val union: CType.Union = typedefs[union_name]!!.type.type as CType.Union
                generator.buildKotlinFile(target, Constants.UNION_PACKAGE_NAME) {
                    val content: String = generateUnion(union_name, union, null, union_name, target) ?: return@buildKotlinFile
                    append(content)
                    files[target_index].add(BindingFile(Constants.UNION_PACKAGE_NAME + '.' + union_name, build()))
                }
            }

            for (enm in used_enums) {
                val def: CType.Enum = (typedefs[enm] ?: throw NullPointerException("$enm ${typedefs["cairo_dither_t"]}")).type.type as CType.Enum
                generator.buildKotlinFile(target, Constants.ENUM_PACKAGE_NAME) {
                    val content: String = target.implementEnumFileContent(def, this) ?: return@buildKotlinFile
                    append(content)
                    files[target_index].add(BindingFile(Constants.ENUM_PACKAGE_NAME + '.' + def.name, build()))
                }
            }

            val all_structs: List<CType.Struct> = used_structs.mapNotNull { typedefs[it]?.type?.type as? CType.Struct }
            for (header in headers) {
                generator.buildKotlinFile(target, package_name) {
                    append(generateHeaderFileContent(header, all_structs))
                    files[target_index].add(BindingFile(package_name + '.' + header.class_name, build()))
                }
            }
        }

        return GeneratedBindings(Constants.TOP_LEVEL_PACKAGE, files)
    }
}
