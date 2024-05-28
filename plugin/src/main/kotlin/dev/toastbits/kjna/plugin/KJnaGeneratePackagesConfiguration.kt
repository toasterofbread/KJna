package dev.toastbits.kjna.plugin

import java.io.Serializable
import java.io.File
import dev.toastbits.kjna.c.CHeaderParser
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
import dev.toastbits.kjna.binder.target.BinderTargetNativeCinterop

data class KJnaGeneratePackagesConfiguration(
    var packages: List<Package> = emptyList()
): Serializable {
    fun add(package_name: String, configure: Package.() -> Unit) {
        packages += listOf(Package(package_name).also { configure(it) })
    }

    data class Package(
        var package_name: String,
        var enabled: Boolean = true,
        var headers: List<KJnaGeneratePackageHeaderConfiguration> = emptyList(),
        var libraries: List<String> = emptyList()
    ): Serializable {
        fun addHeader(header_path: String, class_name: String) {
            headers += listOf(KJnaGeneratePackageHeaderConfiguration(header_path, class_name))
        }
    }
}

data class KJnaGeneratePackageHeaderConfiguration(
    val header_path: String,
    val class_name: String
): Serializable

fun KJnaGeneratePackagesConfiguration.Package.addToCompilation(
    compilation: KotlinNativeCompilation,
    def_file_directory: File
): File {
    val def_file: File = def_file_directory.resolve(package_name + "-" + compilation.target.name + ".def")

    compilation.cinterops.apply {
        create(package_name) { cinterop ->
            cinterop.packageName = BinderTargetNativeCinterop.getNativePackageName(package_name)
            cinterop.defFile(def_file)
        }
    }

    return def_file
}

fun KJnaGeneratePackagesConfiguration.Package.createDefFile(def_file: File, parser: CHeaderParser) {
    if (!def_file.exists()) {
        def_file.ensureParentDirsCreated()
        def_file.createNewFile()
    }

    val linker_opts: List<String> = libraries.map { "-l$it" }
    val header_files: List<String> = headers.map { parser.getHeaderFile(it.header_path).absolutePath }

    def_file.writeText("""
        headers = ${header_files.joinToString(" ")}
        linkerOpts = ${linker_opts.joinToString(" ")}
    """.trimIndent())
}
