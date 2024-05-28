package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.CHeaderParser
import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CTypeDef
import dev.toastbits.kjna.binder.target.KJnaBinderTarget

class KJnaBinder(
    val package_name: String,
    val headers: List<Header>,
    val typedefs: Map<String, CTypeDef>
) {
    data class Header(
        val class_name: String,
        val package_name: String,
        val info: CHeaderParser.HeaderInfo
    )

    data class GeneratedBindings(
        val top_level_package: String,
        val files: Map<KJnaBinderTarget, List<BindingFile>>
    )

    data class BindingFile(
        val class_name: String,
        val content: String
    )

    fun generateBindings(targets: List<KJnaBinderTarget>): GeneratedBindings {
        val files: MutableMap<KJnaBinderTarget, MutableList<BindingFile>> =
            targets.associateWith { mutableListOf<BindingFile>() }.toMutableMap()

        val used_structs: MutableList<String> = headers.flatMap { it.info.structs.map { it.name } }.toMutableList()
        val used_enums: MutableList<String> = mutableListOf()

        val generator: BindingGenerator =
            BindingGenerator(
                this,
                getStructImport = { struct_name ->
                    if (!used_structs.contains(struct_name)) {
                        used_structs.add(struct_name)
                    }
                    return@BindingGenerator Constants.STRUCT_PACKAGE_NAME + '.' + struct_name
                },
                getEnumImport = { enum_name ->
                    if (!used_enums.contains(enum_name)) {
                        used_enums.add(enum_name)
                    }
                    return@BindingGenerator Constants.ENUM_PACKAGE_NAME + '.' + enum_name
                }
            )

        for (target in targets) {
            for (header in headers) {
                generator.buildKotlinFile(target, header.package_name) {
                    // Gather initial references
                    generateHeaderFileContent(header, null)
                }
            }
        }

        generator.getStructImport = { struct_name ->
            if (!used_structs.contains(struct_name)) {
                used_structs.add(struct_name)
            }
            Constants.STRUCT_PACKAGE_NAME + '.' + struct_name
        }

        var i: Int = 0
        while (i < used_structs.size) {
            val struct: CType.Struct = typedefs[used_structs[i++]]!!.type.type as CType.Struct

            for (target in targets) {
                generator.buildKotlinFile(target, Constants.STRUCT_PACKAGE_NAME) {
                    append(generateStructBody(struct, target))
                    files[target]!!.add(BindingFile(Constants.STRUCT_PACKAGE_NAME + '.' + struct.name, build()))
                }
            }
        }

        for (target in targets) {
            for (enm in used_enums) {
                val def: CType.Enum = typedefs[enm]!!.type.type as CType.Enum
                generator.buildKotlinFile(target, Constants.ENUM_PACKAGE_NAME) {
                    val content: String? = target.implementEnumFileContent(def, this)
                    if (content != null) {
                        append(content)
                        files[target]!!.add(BindingFile(Constants.ENUM_PACKAGE_NAME + '.' + def.name, build()))
                    }
                }
            }

            val all_structs: List<CType.Struct> = used_structs.map { typedefs[it]!!.type.type as CType.Struct }
            for (header in headers) {
                generator.buildKotlinFile(target, header.package_name) {
                    append(generateHeaderFileContent(header, all_structs))
                    files[target]!!.add(BindingFile(header.package_name + '.' + header.class_name, build()))
                }
            }
        }


        return GeneratedBindings(Constants.TOP_LEVEL_PACKAGE, files)
    }
}
