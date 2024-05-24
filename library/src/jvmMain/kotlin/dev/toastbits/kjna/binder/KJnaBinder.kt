package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.CValueType
import dev.toastbits.kjna.c.CHeaderParser

class KJnaBinder(
    val headers: List<Header>,
    val typedefs: Map<String, CValueType>,
    val targets: List<KJnaBinderTarget>
) {
    data class Header(
        val class_name: String,
        val package_name: String,
        val info: CHeaderParser.HeaderInfo
    )

    private var used_structs: MutableList<String> = mutableListOf()
    private var used_enums: MutableList<String> = mutableListOf()

    fun generateBindings(): Map<KJnaBinderTarget, String> {
        for (target in targets) {
            for (header in headers) {

                val generator: BinderFileGenerator =
                    BinderFileGenerator(
                        this,
                        header,
                        getStructImport = { struct_name ->
                            if (!used_structs.contains(struct_name)) {
                                used_structs.add(struct_name)
                            }
                            return@BinderFileGenerator header.package_name + "." + struct_name
                        },
                        getEnumImport = { enum_name ->
                            if (!used_enums.contains(enum_name)) {
                                used_enums.add(enum_name)
                            }
                            return@BinderFileGenerator header.package_name + "." + enum_name
                        }
                    )

                val file_content: String = generator.generateFile(target)

                TODO()
            }
        }

        TODO()
    }
}
