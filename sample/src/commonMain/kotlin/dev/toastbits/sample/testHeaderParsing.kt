package dev.toastbits.sample

import dev.toastbits.kjna.c.CHeaderParser
import dev.toastbits.kjna.c.CFunctionDeclaration
import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CTypeDef
import dev.toastbits.kjna.c.resolve

fun testHeaderParsing() {
    println("--- testHeaderParsing() ---")

    val header_path: String = "/usr/include/mpv/client.h"

    val parser: CHeaderParser = CHeaderParser()
    parser.parse(listOf(header_path))

    val typedefs: Map<String, CTypeDef> = parser.getAllTypedefsMap()
    val functions: List<CFunctionDeclaration> = parser.getAllFunctions()

    val header_info: CHeaderParser.HeaderInfo = parser.getHeaderByInclude(header_path)

    for (function in header_info.functions) {
        println("fun ${function.name}(${function.parameters}): ${function.return_type}")
    }
}
