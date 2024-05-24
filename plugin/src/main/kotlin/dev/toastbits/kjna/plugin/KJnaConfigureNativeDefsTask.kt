package dev.toastbits.kjna.plugin

import org.gradle.api.tasks.*
import org.gradle.api.DefaultTask
import java.io.File

abstract class KJnaConfigureNativeDefsTask: DefaultTask() {
    @Input
    lateinit var native_def_files: List<List<File>>

    @Input
    lateinit var packages: KJnaGeneratePackagesConfiguration

    @InputDirectory
    lateinit var native_def_file_directory: File

    @TaskAction
    fun configureNativeDefs() {
        for (file in native_def_file_directory.listFiles() ?: emptyArray()) {
            if (file.name.endsWith(".def")) {
                file.delete()
            }
        }

        for ((index, pkg) in packages.packages.withIndex()) {
            val def_files: List<File> = native_def_files[index]
            for (file in def_files) {
                pkg.createDefFile(file)
            }
        }
    }

    companion object {
        val NAME: String = "kjnaConfigureNativeDefs"
    }
}