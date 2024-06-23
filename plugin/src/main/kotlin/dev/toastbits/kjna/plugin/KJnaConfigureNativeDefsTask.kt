package dev.toastbits.kjna.plugin

import org.gradle.api.tasks.*
import org.gradle.api.DefaultTask
import java.io.File
import dev.toastbits.kjna.c.CHeaderParser
import dev.toastbits.kjna.plugin.options.KJnaCinteropRuntimeOptions
import dev.toastbits.kjna.plugin.options.KJnaGenerationOptions

abstract class KJnaConfigureNativeDefsTask: DefaultTask(), KJnaCinteropRuntimeOptions {
    // Inputs
    override var extra_headers: List<String> = emptyList()

    @Input
    var native_def_files: List<List<Pair<File, KJnaGenerationOptions.Arch>>> = emptyList()

    @Input
    var packages: KJnaGeneratePackagesConfiguration = KJnaGeneratePackagesConfiguration()

    @Input
    lateinit var include_dirs: List<String>

    @Input
    lateinit var arch_include_dirs: Map<KJnaGenerationOptions.Arch, List<String>>

    @Input
    lateinit var lib_dirs: List<String>

    @OutputDirectory
    lateinit var output_directory: File

    @TaskAction
    fun configureNativeDefs() {
        val parser: CHeaderParser = CHeaderParser(include_dirs)

        for (file in output_directory.listFiles() ?: emptyArray()) {
            if (!file.name.endsWith(".def")) {
                continue
            }
            if (native_def_files.any { files -> files.any { it == file } }) {
                continue
            }

            file.delete()
        }

        for ((index, pkg) in packages.packages.withIndex()) {
            val runtime_options: KJnaCinteropRuntimeOptions =
                pkg.cinterop_runtime_options.combine(this)

            val def_files: List<Pair<File, KJnaGenerationOptions.Arch>> = native_def_files[index]
            for ((file, arch) in def_files) {
                pkg.createDefFile(file, parser, runtime_options, include_dirs + arch_include_dirs[arch].orEmpty(), lib_dirs)
            }
        }
    }

    companion object {
        val NAME: String = "kjnaConfigureNativeDefs"
    }
}
