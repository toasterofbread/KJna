package dev.toastbits.sample

import dev.toastbits.kjna.c.CHeaderParser
import dev.toastbits.kjna.c.CFunctionDeclaration
import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CTypeDef
import dev.toastbits.kjna.c.resolve

fun testHeaderParsing() {
    println("--- testHeaderParsing() ---")

    val header_name: String = "mpv/client.h"

    val parser: CHeaderParser = CHeaderParser(listOf("/usr/include/", "/usr/local/include/", "/usr/include/linux/", "C:/msys64/mingw64/include"))
    parser.parse(listOf(header_name))

    val typedefs: Map<String, CTypeDef> = parser.getAllTypedefsMap()
    val functions: List<CFunctionDeclaration> = parser.getAllFunctions()

    val header_info: CHeaderParser.HeaderInfo = parser.getHeaderByInclude(header_name)

    for (function in header_info.functions) {
        println("fun ${function.name}(${function.parameters}): ${function.return_type}")
    }
}
