package dev.toastbits.kjna.plugin

import org.gradle.api.tasks.*
import org.gradle.api.DefaultTask
import java.io.File
import dev.toastbits.kjna.c.CHeaderParser

abstract class KJnaConfigureNativeDefsTask: DefaultTask() {
    @Input
    var native_def_files: List<List<File>> = emptyList()

    @Input
    var packages: KJnaGeneratePackagesConfiguration = KJnaGeneratePackagesConfiguration()

    @Input
    lateinit var include_dirs: List<String>

    @InputDirectory
    lateinit var output_directory: File

    @TaskAction
    fun configureNativeDefs() {
        val parser: CHeaderParser = CHeaderParser(include_dirs)

        for (file in output_directory.listFiles() ?: emptyArray()) {
            if (file.name.endsWith(".def")) {
                file.delete()
            }
        }

        for ((index, pkg) in packages.packages.withIndex()) {
            val def_files: List<File> = native_def_files[index]
            for (file in def_files) {
                pkg.createDefFile(file, parser)
            }
        }
    }

    companion object {
        val NAME: String = "kjnaConfigureNativeDefs"
    }
}