package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.CType

fun generateEnumClass(enm: CType.Enum): String  =
    buildString {
        append("enum class ")
        append(enm.name)
        appendLine("(val value: Int) {")

        for ((name, value) in enm.values) {
            append("    ")
            append(name)
            append('(')
            append(value)
            appendLine("),")
        }

        append("}")
    }
