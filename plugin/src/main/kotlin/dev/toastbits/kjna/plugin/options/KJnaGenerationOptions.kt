package dev.toastbits.kjna.plugin.options

import java.io.File
import org.gradle.api.tasks.*
import dev.toastbits.kjna.plugin.KJnaGeneratePackagesConfiguration
import dev.toastbits.kjna.plugin.KJnaBuildTarget

/**
 * @property packages the packages to generate bindings for and their configurations.
 * @property build_targets bindings for targets in this list will be generated as normal, unless disabled in a specific package.
 * @property disabled_build_targets bindings for targets in this list will be generated in the disabled state. See [dev.toastbits.kjna.plugin.KJnaGeneratePackagesConfiguration.Package.enabled],
 * @property include_dirs paths of directories to search in when looking for C headers to parse.
 * @property parser_include_dirs paths of directories to search in when looking for C headers to parse. Only passed to KJna's internal parser.
 * @property override_jextract_loader if true, the SYMBOL_LOOKUP property of each package's main Jextract class will not be initialised automatically, and must instead be set by the user before being accessed. See [dev.toastbits.kjna.plugin.KJnaGeneratePackageHeaderConfiguration.override_jextract_loader].
 * @property source_output_dir the directory in which to output generated Kotlin source files. Defaults to /<project>/build/kjna/src.
 * @property java_output_dir the directory in which to output Jextract's generated Java source files. KMP will not include Java classes in outputted Jars unless Java source is placed in /<project>/src/jvmMain/java. Defaults to /<project>/src/jvmMain/java/kjna.
 * @property native_def_output_dir the directory in which to output Kotlin/Native cinterop .def files. Defaults to /<project>/build/kjna/def.
 */
interface KJnaGenerationOptions {
    @get:Input
    var packages: KJnaGeneratePackagesConfiguration

    @get:Input
    var build_targets: List<KJnaBuildTarget>

    @get:Input
    var disabled_build_targets: List<KJnaBuildTarget>

    @get:Input
    var include_dirs: List<String>

    @get:Input
    var parser_include_dirs: List<String>

    @get:Input
    var override_jextract_loader: Boolean

    @get:OutputDirectory
    var source_output_dir: File

    @get:OutputDirectory
    var java_output_dir: File

    @get:OutputDirectory
    var native_def_output_dir: File
}
