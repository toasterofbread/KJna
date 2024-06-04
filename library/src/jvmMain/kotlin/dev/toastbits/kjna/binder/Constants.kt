package dev.toastbits.kjna.binder

object Constants {
    const val TOP_LEVEL_PACKAGE: String = "kjna"
    const val STRUCT_PACKAGE_NAME: String = "$TOP_LEVEL_PACKAGE.struct"
    const val ENUM_PACKAGE_NAME: String = "$TOP_LEVEL_PACKAGE.enum"
    const val UNION_PACKAGE_NAME: String = "$TOP_LEVEL_PACKAGE.union"

    fun formatKotlinFieldName(name: String): String =
        when (name) {
            "in" -> "`$name`"
            else -> name
        }
}
