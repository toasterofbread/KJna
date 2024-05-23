package dev.toastbits.kje.c

import dev.toastbits.kje.grammar.*
import dev.toastbits.kje.c.*
import java.io.File
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CharStream
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.tree.*
import org.antlr.v4.kotlinruntime.ParserRuleContext
import java.nio.file.Path
import java.nio.file.Paths

open class CHeaderParser {
    data class HeaderInfo(
        val absolute_path: String,
        val typedefs: List<CTypeDef>,
        val functions: List<CFunctionDeclaration>
    )

    private val parsed_headers: MutableMap<String, HeaderInfo> = mutableMapOf()

    fun getAllHeaders(): List<HeaderInfo> = parsed_headers.values.toList()
    fun getHeaderByInclude(header: String): HeaderInfo = parsed_headers[header]!!

    fun getAllTypedefs(): List<CTypeDef> = getAllHeaders().flatMap { it.typedefs }
    fun getAllTypedefsMap(): Map<String, CValueType> = getAllTypedefs().associate { it.name to it.type }
    fun getAllFunctions(): List<CFunctionDeclaration> = getAllHeaders().flatMap { it.functions }

    fun parse(headers: Set<String>) {
        val all_headers: MutableList<String> = headers.toMutableList()

        var included_headers: List<String> = parseIncludedFiles(headers)
        while (included_headers.isNotEmpty()) {
            all_headers.addAll(included_headers)
            included_headers = parseIncludedFiles(included_headers).filter { !all_headers.contains(it) }
        }

        for (header in all_headers) {
            val functions: MutableList<CFunctionDeclaration> = mutableListOf()
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
                val function: CFunctionDeclaration? = parseFunctionDeclaration(declaration)
                if (function != null) {
                    functions.add(function)
                    continue
                }

                val typedef: CTypeDef? = parseTypedefDeclaration(declaration)
                if (typedef != null) {
                    typedefs.add(typedef)
                    continue
                }
            }

            parsed_headers[header] = HeaderInfo(file.absolutePath, typedefs, functions)
        }
    }

    open fun getIncludeDirs(): List<Path> =
        listOf(
            "/usr/include/",
            "/usr/local/include/",
            "/usr/include/linux/"
        ).map { Paths.get(it) }

    private fun getHeaderFile(path: String): File {
        var file: File = File(path)
        if (file.isFile()) {
            return file
        }

        val include_dirs: List<Path> = getIncludeDirs()
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
