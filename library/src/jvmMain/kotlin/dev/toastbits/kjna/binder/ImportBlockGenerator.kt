package dev.toastbits.kjna.binder

fun generateImportBlock(
    imports: List<BindingGenerator.Import>,
    binder: KJnaBinder,
    anonymous_struct_indices: Map<Int, Int> = emptyMap()
): String = buildString {
    if (imports.isEmpty()) {
        return@buildString
    }

    val import_coordinates: List<Pair<String, String?>> = imports.map {
        val coordinates: String = it.getImportCoordinates(binder)
        val alias: String? = it.getAlias()?.takeIf { it != coordinates.split(".").last() }
        return@map coordinates to alias
    }

    val anonymous_struct_prefix: String = binder.package_name + ".cinterop.anonymousStruct"

    for ((_coordinates, alias) in import_coordinates.distinct().sortedBy { it.first }) {
        var coordinates: String = _coordinates

        if (coordinates.startsWith(anonymous_struct_prefix)) {
            val index: Int? = coordinates.drop(anonymous_struct_prefix.length).toIntOrNull()
            if (index != null) {
                val replacement_index: Int? = anonymous_struct_indices[index]
                if (replacement_index != null) {
                    coordinates = anonymous_struct_prefix + replacement_index.toString()
                }
            }
        }

        append("import $coordinates")

        if (alias != null) {
            append(" as ")
            append(alias)
        }

        appendLine()
    }
    appendLine()
}
