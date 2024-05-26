package dev.toastbits.sample

import dev.toastbits.kjna.c.CHeaderParser
import dev.toastbits.kjna.c.CTypeDef
import dev.toastbits.kjna.c.CFunctionDeclaration
import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CTypeDef
import dev.toastbits.kjna.c.resolve

fun main() {
    val test: String = "/usr/include/mpv/client.h"

    val parser: CHeaderParser = CHeaderParser()
    parser.parse(setOf(test))

    val typedefs: Map<String, CTypeDef> = parser.getAllTypedefsMap()
    val functions: List<CFunctionDeclaration> = parser.getAllFunctions()

    val mpv = parser.getHeaderByInclude(test)

    for (function in mpv.functions) {
        println("Resolving types for function '${function.name}'")
        for (param in function.parameters) {
            val type: CType = param.type.type
            if (type is CType.TypeDef) {
                val name: String = type.name
                println("$name -> ${type.resolve(typedefs)}")
            }
        }

        println("")
    }
}
