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
                val args: MutableList<String> = mutableListOf(
                    "--output", output_directory.absolutePath,
                    "--target-package", BinderTargetJvmJextract.getJvmPackageName(pkg.package_name),
                    "--header-class-name", header.class_name
                )

                for (library in pkg.libraries) {
                    args.add("--library")
                    args.add(library)
                }

                args.add(parser.getHeaderFile(header.header_path).absolutePath)

                executeJextractCommand(args)
            }
        }
    }

    private fun executeJextractCommand(args: List<String>) {
        println("Executing Jextract command: $args")

        val binary: File = jextract_binary
        val process: Process = Runtime.getRuntime().exec((listOf(binary.absolutePath) + args).toTypedArray())

        val result: Int = process.waitFor()
        if (result != 0) {
            throw GradleException("Jextract failed ($result).\nExecutable: $binary\nArgs: $args")
        }
    }

    companion object {
        const val NAME: String = "kjnaJextractGenerate"
    }
}
