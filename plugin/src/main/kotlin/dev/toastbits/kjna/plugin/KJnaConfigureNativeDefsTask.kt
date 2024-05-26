package dev.toastbits.kjna.plugin

import org.gradle.api.tasks.*
import org.gradle.api.DefaultTask
import java.io.File

abstract class KJnaConfigureNativeDefsTask: DefaultTask() {
    @Input
    var native_def_files: List<List<File>> = emptyList()

    @Input
    var packages: KJnaGeneratePackagesConfiguration = KJnaGeneratePackagesConfiguration()

    @InputDirectory
    lateinit var native_def_output_dir: File

    @TaskAction
    fun configureNativeDefs() {
        for (file in native_def_output_dir.listFiles() ?: emptyArray()) {
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