package dev.toastbits.kjna.plugin

import org.gradle.api.tasks.*
import org.gradle.api.DefaultTask
import java.io.File
import org.gradle.api.GradleException
import dev.toastbits.kjna.c.CHeaderParser
import dev.toastbits.kjna.binder.target.KJnaBindTargetJvmJextract
import dev.toastbits.kjna.plugin.options.KJnaJextractRuntimeOptions
import kotlin.text.Regex

/**
 * Generates Java bindings for native packages using a local Jextract binary.
 *
 * @property jextract_binary
 * @property runtime_options generation options specific to Jextract. Can also be configured per-package.
 */
open class KJnaJextractGenerateTask: DefaultTask(), KJnaJextractRuntimeOptions {
    // Inputs
    override var symbol_filter: List<String> = emptyList()
    override var macros: List<String> = emptyList()
    override var args: List<String> = emptyList()

    @InputFile
    lateinit var jextract_binary: File

    @Input
    lateinit var include_dirs: List<String>

    @Input
    lateinit var packages: KJnaGeneratePackagesConfiguration

    @Input
    var override_jextract_loader: Boolean = false

    @OutputDirectory
    lateinit var output_directory: File

    @TaskAction
    fun jextractGenerate() {
        val parser: CHeaderParser = CHeaderParser(include_dirs)

        if (output_directory.exists()) {
            output_directory.deleteRecursively()
        }

        val temp_symbols_file: File by lazy { temporaryDir.resolve("jextract_symbols.txt").apply { createNewFile() } }

        for (pkg in packages.packages) {
            val runtime_options: KJnaJextractRuntimeOptions =
                pkg.jextract_runtime_options.combine(this)

            val symbol_filter_regex: List<Regex> = runtime_options.symbol_filter.map { Regex(it) }

            for (header in pkg.headers) {
                val header_class_name: String = header.class_name ?: continue

                val target_package: String = KJnaBindTargetJvmJextract.getJvmPackageName(pkg.package_name)
                val args: MutableList<String> =
                    mutableListOf(
                        parser.getHeaderFile(header.header_path).absolutePath,
                        "--target-package", target_package,
                        "--header-class-name", header_class_name
                    )

                for (macro in runtime_options.macros) {
                    args.add("--define-macro")
                    args.add(macro)
                }

                for (arg in runtime_options.args) {
                    args.add(arg)
                }

                for (include_dir in (pkg.include_dirs + include_dirs).distinct()) {
                    args.add("--include-dir")
                    args.add(include_dir)
                }

                for (library in pkg.libraries) {
                    args.add("--library")
                    args.add(library)
                }

                if (symbol_filter_regex.isNotEmpty()) {
                    println("Dumping includes for $header...")
                    executeJextractCommand(args + listOf("--dump-includes", temp_symbols_file.absolutePath))

                    println("Filtering includes for $header...")
                    val args_file_content: StringBuilder = StringBuilder()

                    temp_symbols_file.useLines() { lines ->
                        for (line in lines) {
                            val start: Int = line.indexOf(' ')
                            val end: Int = line.indexOf('#')
                            if (start == -1 || end == -1 || start >= end) {
                                continue
                            }

                            val symbol: String = line.substring(start + 1, end).trim()
                            if (symbol_filter_regex.any { it.containsMatchIn(symbol) }) {
                                args_file_content.append(line.substring(0, start))
                                args_file_content.append(' ')
                                args_file_content.appendLine(symbol)
                            }
                        }
                    }

                    check(args_file_content.isNotEmpty()) { "No symbols match filter for $header in $pkg" }

                    temp_symbols_file.writeText(args_file_content.toString())
                    args.add("@${temp_symbols_file.absolutePath}")
                }

                println("Extracting $header...")
                executeJextractCommand(args + listOf("--output", output_directory.absolutePath))

                if (header.override_jextract_loader ?: override_jextract_loader) {
                    val main_class_dir: File = output_directory.resolve(target_package.replace(".", "/"))

                    var cls: String? = header.class_name
                    while (cls != null) {
                        val main_file: File = main_class_dir.resolve(cls + ".java")
                        check(main_file.isFile) { "${main_file.name} not found in $main_class_dir" }

                        cls = overrideJextractLoader(main_file, cls)
                    }
                }
            }
        }
    }

    private fun executeJextractCommand(args: List<String>) {
        val binary: File = jextract_binary

        println("Executing Jextract command: $binary ${args.joinToString(" ")}")

        val process: Process =
            ProcessBuilder(listOf(binary.absolutePath) + args).start()

        val stderr: String = process.errorStream.reader().readText()

        val result: Int = process.waitFor()
        if (result != 0) {
            throw GradleException("Jextract failed ($result).\nCommand: $binary ${args.joinToString(" ")}\nstderr: $stderr")
        }
    }

    private fun overrideJextractLoader(main_file: File, class_name: String): String? {
        val content: MutableList<String> = main_file.readLines().toMutableList()
        val content_iterator: MutableListIterator<String> = content.listIterator()

        try {
            var symbol_lookup_found: Boolean = false
            var class_found: Boolean = false
            var package_found: Boolean = false

            for (line in content_iterator) {
                if (line.startsWith("package ")) {
                    check(!package_found) { "Duplicate package declaration" }
                    package_found = true

                    val spacer: String = content_iterator.next()
                    check(spacer == "") { "Unexpected shape '$spacer'" }
                    content_iterator.add("import java.nio.file.Path;")
                }
                else if (line.startsWith("public class $class_name ")) {
                    val extends: Int = line.indexOf(" extends ")
                    if (extends != -1) {
                        val parent_end: Int = line.indexOf(' ', extends + 10)
                        check(parent_end != -1) { line }

                        val parent_class: String = line.substring(extends + 9, parent_end)
                        return parent_class
                    }

                    check(!class_found) { "Duplicate class '$class_name'" }
                    class_found = true

                    content_iterator.add("")
                    for (line in JEXTRACT_SYMBOL_LOOKUP_SETTER.trim('\n').split("\n")) {
                        content_iterator.add(line)
                    }
                }
                else if (line.startsWith("    static final SymbolLookup SYMBOL_LOOKUP = ")) {
                    check(!symbol_lookup_found) { "Duplicate SymbolLookup" }
                    symbol_lookup_found = true

                    content_iterator.set("    static SymbolLookup SYMBOL_LOOKUP = null;")

                    while (content_iterator.next().trim().startsWith(".or(")) {
                        content_iterator.remove()
                    }
                }
            }

            check(package_found) { "Package declaration not found" }
            check(class_found) { "Class '$class_name' not found" }
            check(symbol_lookup_found) { "SymbolLookup not found" }
        }
        catch (e: Throwable) {
            throw RuntimeException("Exception while processing main Jextract file ($main_file) on line ${content_iterator.previousIndex()}", e)
        }

        main_file.writeText(content.joinToString("\n"))
        return null
    }

    companion object {
        const val NAME: String = "kjnaJextractGenerate"

        private const val JEXTRACT_SYMBOL_LOOKUP_SETTER: String =
"""
    public static void setLibraryByPath(Path path) {
        SYMBOL_LOOKUP = SymbolLookup.libraryLookup(path, LIBRARY_ARENA);
    }

    public static void setLibraryByName(String name) {
        SYMBOL_LOOKUP = SymbolLookup.libraryLookup(System.mapLibraryName(name), LIBRARY_ARENA);
    }
"""
    }
}
