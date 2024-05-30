package dev.toastbits.kjna.plugin

import org.gradle.api.tasks.*
import org.gradle.api.DefaultTask
import java.io.File
import org.gradle.api.GradleException
import dev.toastbits.kjna.c.CHeaderParser
import dev.toastbits.kjna.binder.target.BinderTargetJvmJextract

open class KJnaJextractGenerateTask: DefaultTask() {
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

        for (pkg in packages.packages) {
            for (header in pkg.headers) {
                val target_package: String = BinderTargetJvmJextract.getJvmPackageName(pkg.package_name)
                val args: MutableList<String> = mutableListOf(
                    "--output", output_directory.absolutePath,
                    "--target-package", target_package,
                    "--header-class-name", header.class_name
                )

                for (library in pkg.libraries) {
                    args.add("--library")
                    args.add(library)
                }

                args.add(parser.getHeaderFile(header.header_path).absolutePath)

                executeJextractCommand(args)

                if (header.override_jextract_loader ?: override_jextract_loader) {
                    val main_class_dir: File = output_directory.resolve(target_package.replace(".", "/"))
                    val main_file: File = main_class_dir.resolve(header.class_name + ".java")
                    check(main_file.isFile) { "${main_file.name} not found in $main_class_dir" }

                    overrideJextractLoader(main_file, header.class_name)
                }
            }
        }
    }

    private fun executeJextractCommand(args: List<String>) {
        println("Executing Jextract command: $args")

        val binary: File = jextract_binary
        val process: Process = Runtime.getRuntime().exec((listOf(binary.absolutePath) + args).toTypedArray())

        val result: Int = process.waitFor()
        if (result != 0) {
            throw GradleException("Jextract failed ($result).\nExecutable: $binary\nArgs: ${args.joinToString(" ")}")
        }
    }

    private fun overrideJextractLoader(main_file: File, class_name: String) {
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
                else if (line == "public class $class_name {") {
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
                    
                    check(content_iterator.next().trim() == ".or(SymbolLookup.loaderLookup())") { "Unexpected shape (1)" }
                    content_iterator.remove()
                    check(content_iterator.next().trim() == ".or(Linker.nativeLinker().defaultLookup());") { "Unexpected shape (2)" }
                    content_iterator.remove()
                }
            }

            check(package_found) { "Package declaration not found" }
            check(class_found) { "Class '$class_name' not found" }
            check(symbol_lookup_found) { "SymbolLookup not found" }
        }
        catch (e: Throwable) {
            throw RuntimeException("Exception while processing main Jextract file ($main_file) on line ${content_iterator.previousIndex()}")
        }

        main_file.writeText(content.joinToString("\n"))
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
