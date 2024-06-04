package dev.toastbits.kjna.binder

fun generateImportBlock(imports: List<BindingGenerator.Import>, binder: KJnaBinder): String = buildString {
    if (imports.isEmpty()) {
        return@buildString
    }

    val import_coordinates: List<Pair<String, String?>> = imports.map {
        val coordinates: String = it.getImportCoordinates(binder)
        val alias: String? = it.getAlias()?.takeIf { it != coordinates.split(".").last() }
        return@map coordinates to alias
    }

    for ((coordinates, alias) in import_coordinates.distinct().sortedBy { it.first }) {
        append("import $coordinates")

        if (alias != null) {
            append(" as ")
            append(alias)
        }

        appendLine()
    }
    appendLine()
}
