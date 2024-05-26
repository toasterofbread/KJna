package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.CHeaderParser
import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CTypeDef

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
        val files: MutableMap<KJnaBinderTarget, MutableList<BindingFile>> = mutableMapOf()

        val used_structs: MutableList<String> = mutableListOf()
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
            val target_files: MutableList<BindingFile> = mutableListOf()
            files[target] = target_files

            for (header in headers) {
                val file_content: String = generator.generateHeaderFile(header, target)
                target_files.add(BindingFile(header.package_name + '.' + header.class_name, file_content))
            }
        }

        val new_structs: MutableList<String> = used_structs.toMutableList()
        generator.getStructImport = { struct_name ->
            if (!used_structs.contains(struct_name)) {
                used_structs.add(struct_name)
                new_structs.add(struct_name)
            }
            Constants.STRUCT_PACKAGE_NAME + '.' + struct_name
        }

        while (new_structs.isNotEmpty()) {
            val struct: CType.Struct = typedefs[new_structs.removeLast()]!!.type.type as CType.Struct

            for (target in targets) {
                val file_content: String = buildString {
                    appendLine("package ${Constants.STRUCT_PACKAGE_NAME}")
                    appendLine()
                    append(generateStruct(struct, target, generator))
                    appendLine()
                }

                files[target]!!.add(BindingFile(Constants.STRUCT_PACKAGE_NAME + '.' + struct.name, file_content))
            }
        }

        val enum_targets: List<KJnaBinderTarget> =
            if (targets.contains(KJnaBinderTarget.SHARED)) listOf(KJnaBinderTarget.SHARED)
            else targets

        for (enm in used_enums) {
            for (target in enum_targets) {
                val def: CType.Enum = typedefs[enm]!!.type.type as CType.Enum

                val file_content: String = buildString {
                    appendLine("package ${Constants.ENUM_PACKAGE_NAME}")
                    appendLine()
                    append(generateEnumClass(def))
                    appendLine()
                }

                files[target]!!.add(BindingFile(Constants.ENUM_PACKAGE_NAME + '.' + def.name, file_content))
            }
        }

        return GeneratedBindings(Constants.TOP_LEVEL_PACKAGE, files)
    }
}
