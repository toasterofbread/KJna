package dev.toastbits.kjna.c

import dev.toastbits.kjna.grammar.*
import dev.toastbits.kjna.c.*
import java.io.File
import org.antlr.v4.kotlinruntime.CharStream
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.tree.*
import org.antlr.v4.kotlinruntime.ParserRuleContext
import org.antlr.v4.kotlinruntime.misc.Interval
import org.anarres.cpp.*
import java.nio.file.Path
import java.nio.file.Paths

class CHeaderParser(include_dirs: List<String>) {
    data class HeaderInfo(
        val absolute_path: String,
        val functions: Map<String, CFunctionDeclaration>,
        val structs: Map<String, CType.Struct>,
        val typedefs: Map<String, CTypedef>
    ) {
        override fun toString(): String =
            "HeaderInfo(absolute_path=$absolute_path, functions: ${functions.size}), structs: ${structs.size}, typedefs: ${typedefs.size}"
    }

    private val parsed_headers: MutableMap<String, HeaderInfo> = mutableMapOf()

    private val include_dirs: List<Path> = include_dirs.map { Paths.get(it) }
    private var all_include_dirs: List<Path> = this.include_dirs

    fun getAllHeaders(): List<HeaderInfo> = parsed_headers.values.toList()
    fun getHeaderByInclude(header: String): HeaderInfo = parsed_headers[getHeaderFile(header).absolutePath]!!

    fun getConstantExpressionValue(name: String): Int? {
        for (header in parsed_headers.values) {
            val typedef: CTypedef = header.typedefs[name] ?: continue
            if (typedef.type.type is CType.Enum) {
                for ((key, value) in typedef.type.type.values) {
                    if (key == name) {
                        return value
                    }
                }
            }
        }
        return null
    }

    fun getTypedef(name: String): CTypedef? {
        for (header in parsed_headers.values) {
            header.typedefs[name]?.also { return it }
        }
        return null
    }

    fun getAllTypedefsMap(): Map<String, CTypedef> = parsed_headers.values.flattenMaps { it.typedefs }
    fun getAllFunctions(): Map<String, CFunctionDeclaration> = parsed_headers.values.flattenMaps { it.functions }

    fun parse(
        headers: List<String>,
        package_scope: PackageGenerationScope,
        extra_include_dirs: List<String> = emptyList(),
        ignore_headers: List<String> = emptyList()
    ) {
        val file_content: StringBuilder by lazy { StringBuilder() }

        withExtraIncludeDirs(extra_include_dirs) {
            val preprocessor_listener: PreprocessorListener =
                object : DefaultPreprocessorListener() {
                    override fun handleWarning(source: Source, line: Int, column: Int, msg: String) {
                        println("Warning: $source $line:$column $msg")
                    }
                }

            for (header_path in headers) {
                val file: File = getHeaderFile(header_path)
                println("Parsing $header_path at ${file.absolutePath}")

                file_content.clear()

                val preprocessor =
                    object : Preprocessor() {
                        init {
                            listener = preprocessor_listener
                        }

                        override public fun getSource(): Source? = super.source

                        override fun include(path: Iterable<String>, name: String): Boolean {
                            val path: List<String> = path.toList()
                            val include_path: String =
                                if (path.isEmpty()) name
                                else (path.joinToString("/") + '/' + name)

                            var header: File = getHeaderFileOrNull(include_path) ?: return false
                            include(fileSystem.getFile(header.absolutePath))

                            return true
                        }
                    }

                preprocessor.addInput(
                    object : LexerSource(file.reader(), true) {
                        override fun getPath(): String = file.absolutePath
                    }
                )

                var token: Token
                var current_source_path: String? = null

                while (true) {
                    token = preprocessor.token()

                    if (current_source_path == null) {
                        current_source_path = preprocessor.source!!.path!!
                    }

                    if (token.type == Token.EOF || preprocessor.source!!.path != current_source_path) {
                        if (file_content.isNotBlank()) {
                            val current_header_path: String =
                                if (token.type == Token.EOF) current_source_path!!
                                else current_source_path!!

                            try {
                                package_scope.parseFileContent(CharSequenceCharStream(file_content), current_header_path)
                            }
                            catch (e: Throwable) {
                                throw RuntimeException("parseFileContent failed ($current_header_path)", e)
                            }
                        }

                        if (token.type == Token.EOF) {
                            break
                        }

                        file_content.clear()
                        current_source_path = preprocessor.source!!.path!!
                    }

                    file_content.append(token.text)
                }

                preprocessor.close()
            }
        }
    }

    fun getHeaderFileOrNull(path: String): File? {
        var file: File = File(path)
        if (file.isFile()) {
            return file
        }

        for (dir in all_include_dirs) {
            file = dir.resolve(path).toFile()
            if (file.isFile()) {
                return file
            }
        }

        return null
    }

    fun getHeaderFile(path: String): File =
        getHeaderFileOrNull(path) ?: throw RuntimeException("Could not find header file for '$path' relatively or in $all_include_dirs")

    private fun withExtraIncludeDirs(extra_include_dirs: List<String>, action: () -> Unit) {
        all_include_dirs = include_dirs + extra_include_dirs.map { Paths.get(it) }
        try {
            action()
        }
        finally {
            all_include_dirs = include_dirs
        }
    }

    private fun PackageGenerationScope.parseFileContent(source: CharStream, header_path: String) {
        val lexer: CLexer = CLexer(source)
        val tokens: CommonTokenStream = CommonTokenStream(lexer)

        val parser: CParser = CParser(tokens)
        parser.errorHandler = SilentErrorStrategy()

        val tree: CParser.TranslationUnitContext = parser.translationUnit()

        val info: HeaderInfo = parsed_headers.getOrPut(header_path) { HeaderInfo(header_path, mutableMapOf(), mutableMapOf(), mutableMapOf()) }
        val functions: MutableMap<String, CFunctionDeclaration> = info.functions as MutableMap<String, CFunctionDeclaration>
        val structs: MutableMap<String, CType.Struct> = info.structs as MutableMap<String, CType.Struct>
        val typedefs: MutableMap<String, CTypedef> = info.typedefs as MutableMap<String, CTypedef>

        for (declaration in tree.externalDeclaration()) {
            val function: CFunctionDeclaration? = declaration.declaration()?.let { parseFunctionDeclaration(it) }
            if (function != null) {
                val existing: CFunctionDeclaration? = functions[function.name]
                check(existing == null || existing == function) { "Redeclaration of $function (existing=$existing) along $header_path" }
                functions[function.name] = function
                continue
            }

            val typedef: CTypedef? =
                try {
                    parseTypedefDeclaration(declaration)
                }
                catch (e: Throwable) {
                    throw RuntimeException("parseTypedefDeclaration failed (${declaration.text})", e)
                }

            if (typedef != null) {
                val existing: CTypedef? = typedefs[typedef.name]
                if ((existing != null && typedef.type.type.isForwardDeclaration()) || existing == typedef) {
                    continue
                }

                check(existing == null || existing.type.type.isForwardDeclaration()) {
                    "Redeclaration of $typedef (existing=$existing) along $header_path"
                }

                typedefs[typedef.name] = typedef

                val type_name: String? =
                    when (typedef.type.type) {
                        is CType.Enum -> typedef.type.type.name
                        is CType.Struct -> typedef.type.type.name
                        is CType.Union -> typedef.type.type.name
                        else -> null
                    }

                if (type_name != null && type_name != typedef.name && !typedefs.contains(type_name)) {
                    typedefs[type_name] = typedef
                }

                if (typedef.type.type is CType.Struct) {
                    structs[typedef.name] = typedef.type.type
                }

                continue
            }
        }
    }
}

private fun <K, V, I> Collection<I>.flattenMaps(onConflict: ((key: K, value: V, existing_value: V) -> V)? = null, getMap: (I) -> Map<K, V>): Map<K, V> {
    val flat_map: MutableMap<K, V> = mutableMapOf()
    for (map in this) {
        for ((key, value) in getMap(map)) {
            if (onConflict != null && flat_map.contains(key)) {
                @Suppress("UNCHECKED_CAST")
                val existing: V = flat_map[key] as V
                flat_map[key] = onConflict(key, value, existing)
            }
            else {
                flat_map[key] = value
            }
        }
    }
    return flat_map
}
