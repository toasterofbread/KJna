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

open class CHeaderParser(include_dirs: List<String>) {
    data class PackageInfo(
        val headers: Map<String, Header>,
        val structs: Map<String, CType.Struct>,
        val typedefs: Map<String, CTypedef>
    ) {
        data class Header(
            val functions: Map<String, CFunctionDeclaration>
        ) {
            override fun toString(): String =
                "Header(functions: ${functions.size})"
        }

        override fun toString(): String =
            "PackageInfo(headers: $headers), structs: ${structs.size}, typedefs: ${typedefs.size}"
    }

    private val parsed_header_functions: MutableMap<String, MutableMap<String, CFunctionDeclaration>> = mutableMapOf()
    private val parsed_structs: MutableMap<String, CType.Struct> = mutableMapOf()
    private val parsed_typedefs: MutableMap<String, CTypedef> = mutableMapOf()

    private val include_dirs: List<Path> = (include_dirs + getEnvironmentIncludeDirs()).map { Paths.get(it) }
    private var all_include_dirs: List<Path> = this.include_dirs
    private var typedef_overrides: Map<String, CValueType> = emptyMap()

    fun getHeaderByInclude(pkg: PackageInfo, header: String): PackageInfo.Header = pkg.headers[getHeaderFile(header).absolutePath]!!

    fun parsePackage(
        headers: List<String>,
        extra_include_dirs: List<String> = emptyList(),
        ignore_headers: List<String> = emptyList(),
        typedef_overrides: Map<String, CValueType> = emptyMap()
    ): PackageInfo = withExtras(extra_include_dirs, typedef_overrides) {
        parsed_header_functions.clear()
        parsed_structs.clear()
        parsed_typedefs.clear()

        val file_content: StringBuilder by lazy { StringBuilder() }
        val preprocessor_listener: PreprocessorListener =
            object : DefaultPreprocessorListener() {
                override fun handleWarning(source: Source, line: Int, column: Int, msg: String) {
                    println("Warning: $source $line:$column $msg")
                }
            }

        val package_scope: PackageGenerationScope = PackageGenerationScope(this)

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
                            package_scope.parseFileContent(
                                source = CharSequenceCharStream(file_content),
                                header_path = current_header_path,
                                is_main_header = current_header_path == file.absolutePath
                            )
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

        return@withExtras PackageInfo(
            headers = parsed_header_functions.entries.associate { (header, functions) -> header to PackageInfo.Header(functions = functions) },
            structs = parsed_structs.toMap(),
            typedefs = parsed_typedefs.toMap()
        )
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

    open fun getEnvironmentIncludeDirs(): List<String> {
        val dirs: MutableList<String> = System.getenv("C_INCLUDE_PATH")?.split(":")?.filter { it.isNotBlank() }.orEmpty().toMutableList()

        System.getenv("NIX_CFLAGS_COMPILE")?.also { nix_cflags_compile ->
            var head: Int = 0
            while (true) {
                val include_start: Int = nix_cflags_compile.indexOf("-isystem", head)
                if (include_start == -1) {
                    break
                }

                val path_start: Int = nix_cflags_compile.indexOfFirst(include_start + 8) { !it.isWhitespace() }
                if (path_start == -1) {
                    break
                }

                var path_end: Int = nix_cflags_compile.indexOf(' ', path_start)
                if (path_end == -1) {
                    dirs.add(nix_cflags_compile.substring(path_start))
                    break
                }

                dirs.add(nix_cflags_compile.substring(path_start, path_end))
                head = path_end
            }
        }

        return dirs
    }

    internal fun getTypedef(name: String): CTypedef? = typedef_overrides[name]?.let { CTypedef(name, it) } ?: parsed_typedefs[name]

    internal fun getConstantExpressionValue(name: String): Int? {
        for (type in typedef_overrides.values) {
            if (type.type is CType.Enum) {
                for ((key, value) in type.type.values) {
                    if (key == name) {
                        return value
                    }
                }
            }
        }
        for (typedef in parsed_typedefs.values) {
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

    private fun <T> withExtras(
        extra_include_dirs: List<String>,
        typedef_overrides: Map<String, CValueType>,
        action: () -> T
    ): T {
        this.typedef_overrides = typedef_overrides
        all_include_dirs = include_dirs + extra_include_dirs.map { Paths.get(it) }
        try {
            return action()
        }
        finally {
            all_include_dirs = include_dirs
            this.typedef_overrides = emptyMap()
        }
    }

    private fun PackageGenerationScope.parseFileContent(source: CharStream, header_path: String, is_main_header: Boolean) {
        val lexer: CLexer = CLexer(source)
        val tokens: CommonTokenStream = CommonTokenStream(lexer)

        val parser: CParser = CParser(tokens)
        parser.errorHandler = SilentErrorStrategy()

        val tree: CParser.TranslationUnitContext = parser.translationUnit()

        val functions: MutableMap<String, CFunctionDeclaration> = parsed_header_functions.getOrPut(header_path) { mutableMapOf() }

        for (declaration in tree.externalDeclaration()) {
            val typedef: CTypedef? =
                try {
                    parseTypedefDeclaration(declaration)
                }
                catch (e: Throwable) {
                    throw RuntimeException("parseTypedefDeclaration failed (${declaration.text})", e)
                }

            if (typedef != null) {
                val existing: CTypedef? = parsed_typedefs[typedef.name]
                if ((existing != null && typedef.type.type.isForwardDeclaration()) || existing == typedef) {
                    continue
                }

                check(existing == null || existing.type.type.isForwardDeclaration()) {
                    "Redeclaration of $typedef (existing=$existing) along $header_path"
                }

                parsed_typedefs[typedef.name] = typedef

                val type_name: String? =
                    when (typedef.type.type) {
                        is CType.Enum -> typedef.type.type.name
                        is CType.Struct -> typedef.type.type.name
                        is CType.Union -> typedef.type.type.name
                        else -> null
                    }

                if (type_name != null && type_name != typedef.name && !parsed_typedefs.contains(type_name)) {
                    parsed_typedefs[type_name] = typedef
                }

                if (is_main_header && typedef.type.type is CType.Struct) {
                    parsed_structs[typedef.name] = typedef.type.type
                }

                continue
            }

            val function: CFunctionDeclaration? = declaration.declaration()?.let { parseFunctionDeclaration(it) }
            if (function != null) {
                val existing: CFunctionDeclaration? = functions[function.name]
                check(existing == null || existing == function) { "Redeclaration of $function (existing=$existing) along $header_path" }
                functions[function.name] = function
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

fun String.indexOfFirst(from: Int, predicate: (Char) -> Boolean): Int =
    substring(from).indexOfFirst(predicate).takeIf { it != -1 }?.plus(from) ?: -1
