package dev.toastbits.kjna.binder

fun generateImportBlock(imports: List<Pair<String, String?>>): String = buildString {
    if (imports.isNotEmpty()) {
        for ((import, alias) in imports.distinct().sortedBy { it.first }) {
            append("import $import")

            if (alias != null) {
                val last_dot: Int = import.lastIndexOf('.')
                val import_name: String =
                    if (last_dot == -1) import
                    else import.substring(last_dot + 1)

                if (alias != import_name) {
                    append(" as ")
                    append(alias)
                }
            }

            appendLine()
        }
        appendLine()
    }
}
