package dev.toastbits.sample

import dev.toastbits.kjna.c.CHeaderParser
import dev.toastbits.kjna.c.CFunctionDeclaration
import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CTypedef
import dev.toastbits.kjna.c.resolve

fun testHeaderParsing() {
    println("--- testHeaderParsing() ---")

    val parser: CHeaderParser = CHeaderParser(listOf("/usr/include/", "/usr/include/linux/", "C:/msys64/mingw64/include/"))

    val header_name: String = "mpv/client.h"
    val package_info: CHeaderParser.PackageInfo = parser.parsePackage(listOf(header_name))

    for ((file, header) in package_info.headers) {
        println("Header $file")
        for (function in header.functions.values) {
            println("fun ${function.name}(${function.parameters}): ${function.return_type}")
        }
    }
}
