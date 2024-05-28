package dev.toastbits.kjna.c

import dev.toastbits.kjna.grammar.*
import dev.toastbits.kjna.c.*
import java.io.File
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CharStream
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.tree.*
import org.antlr.v4.kotlinruntime.ParserRuleContext
import java.nio.file.Path
import java.nio.file.Paths

class CHeaderParser(include_dirs: List<String>? = null) {
    data class HeaderInfo(
        val absolute_path: String,
        val functions: List<CFunctionDeclaration>,
        val structs: List<CType.Struct>,
        val typedefs: List<CTypeDef>
    ) {
        override fun toString(): String =
            "HeaderInfo(absolute_path=$absolute_path, functions: ${functions.size}), structs: ${structs.size}, typedefs: ${typedefs.size}"
    }

    private val parsed_headers: MutableMap<String, HeaderInfo> = mutableMapOf()
    val include_dirs: List<Path> =
        (include_dirs ?: listOf("/usr/include/", "/usr/local/include/", "/usr/include/linux/")).map { Paths.get(it) }

    fun getAllHeaders(): List<HeaderInfo> = parsed_headers.values.toList()
    fun getHeaderByInclude(header: String): HeaderInfo = parsed_headers[header]!!

    fun getAllTypedefs(): List<CTypeDef> = getAllHeaders().flatMap { it.typedefs }
    fun getAllTypedefsMap(): Map<String, CTypeDef> = getAllTypedefs().associate { it.name to it }
    fun getAllFunctions(): List<CFunctionDeclaration> = getAllHeaders().flatMap { it.functions }

    fun parse(headers: List<String>, package_scope: PackageGenerationScope) {
        val all_headers: MutableList<String> = headers.distinct().toMutableList()

        var included_headers: List<String> = parseIncludedFiles(headers)
        while (included_headers.isNotEmpty()) {
            all_headers.addAll(included_headers)
            included_headers = parseIncludedFiles(included_headers).filter { !all_headers.contains(it) }
        }

        for (header in all_headers) {
            val functions: MutableList<CFunctionDeclaration> = mutableListOf()
            val structs: MutableList<CType.Struct> = mutableListOf()
            val typedefs: MutableList<CTypeDef> = mutableListOf()

            val file: File = getHeaderFile(header)
            println("Parsing ${file.absolutePath}")

            val input: CharStream = file.inputStream().use { stream ->
                CharStreams.fromStream(stream)
            }

            val lexer: CLexer = CLexer(input)
            val tokens: CommonTokenStream = CommonTokenStream(lexer)
            val parser: CParser = CParser(tokens)

            val tree: CParser.TranslationUnitContext = parser.translationUnit()

            for (declaration in tree.externalDeclaration()) {
                val function: CFunctionDeclaration? = with (package_scope) { parseFunctionDeclaration(declaration) }
                if (function != null) {
                    functions.add(function)
                    continue
                }

                val typedef: CTypeDef? = with (package_scope) { parseTypedefDeclaration(declaration) }
                if (typedef != null) {
                    typedefs.add(typedef)

                    if (typedef.type.type is CType.Struct) {
                        structs.add(typedef.type.type)
                    }

                    continue
                }
            }

            parsed_headers[header] = HeaderInfo(file.absolutePath, functions, structs, typedefs)
        }
    }

    fun getHeaderFile(path: String): File {
        var file: File = File(path)
        if (file.isFile()) {
            return file
        }

        for (dir in include_dirs) {
            file = dir.resolve(path).toFile()
            if (file.isFile()) {
                return file
            }
        }

        throw RuntimeException("Could not find header file for '$path' relatively or in $include_dirs")
    }

    private fun parseIncludedFiles(from: Collection<String>): List<String> =
        from.flatMap { file ->
            getHeaderFile(file).readLines().mapNotNull {
                val line: String = it.trim()
                if (!line.startsWith("#include ")) {
                    return@mapNotNull null
                }

                val start: Char = line[9]
                val end: Char =
                    when (start) {
                        '"' -> '"'
                        '<' -> '>'
                        else -> throw NotImplementedError(line)
                    }

                val end_index: Int = line.indexOf(end, 9)
                check(end_index != -1) { line }

                return@mapNotNull line.substring(10, end_index)
            }
        }.filter { !from.contains(it) }.distinct()
}
