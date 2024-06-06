package dev.toastbits.kjna.plugin

import java.io.Serializable
import java.io.File
import dev.toastbits.kjna.c.CHeaderParser
import dev.toastbits.kjna.c.CValueType
import dev.toastbits.kjna.c.CType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
import dev.toastbits.kjna.binder.target.KJnaBindTargetNativeCinterop
import dev.toastbits.kjna.plugin.options.KJnaJextractRuntimeOptions
import dev.toastbits.kjna.plugin.options.KJnaJextractRuntimeOptionsImpl
import dev.toastbits.kjna.plugin.options.KJnaCinteropRuntimeOptions
import dev.toastbits.kjna.plugin.options.KJnaCinteropRuntimeOptionsImpl
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class KJnaGeneratePackagesConfiguration(
    var packages: List<Package> = emptyList()
): Serializable {
    fun add(package_name: String, configure: Package.() -> Unit) {
        packages += listOf(Package(package_name).also { configure(it) })
    }

    /**
     * KJna package binding generation configuration
     *
     * @property package_name the package name to use for generated source files.
     * @property enabled if set to false, this package will be generated with no implementation. Use the `isAvailable()` companion method on genearated header classes to check availability at runtime.
     * @property a list of targets for which bindings will be generated with no implementation. Use the `isAvailable()` companion method on genearated header classes to check availability at runtime.
     * @property headers a list of configurations for C headers to generate bindings for.
     * @property libraries a list of shared library names to link this package against (e.g. 'curl', not 'libcurl' or 'libcurl.so').
     * @property include_dirs paths of directories to search in when looking for C headers to parse.
     * @property parser_ignore_headers a list of headers that should be ignored by KJna's parser.
     * @property overrides configuration for package-specific overrides to the binding generation process.
     * @property jextract_runtime_options options specific to jextract. Can also be configured for the entire generation.
     */
    data class Package(
        var package_name: String,
        var enabled: Boolean = true,
        var disabled_targets: List<KJnaBuildTarget> = emptyList(),
        var headers: List<KJnaGeneratePackageHeaderConfiguration> = emptyList(),
        var libraries: List<String> = emptyList(),
        var include_dirs: List<String> = emptyList(),
        var parser_ignore_headers: List<String> = emptyList(),
        var overrides: PackageOverrides = PackageOverrides(),
        var jextract_runtime_options: KJnaJextractRuntimeOptionsImpl = KJnaJextractRuntimeOptionsImpl(),
        var cinterop_runtime_options: KJnaCinteropRuntimeOptionsImpl = KJnaCinteropRuntimeOptionsImpl()
    ): Serializable {

        /**
         * Adds a single header file to the package configuration.
         * Headers included by this header are parsed recusrively.
         *
         * @param header_path the path to the header file, relative to an include directory.
         * @param class_name the name of the class to generate for this header file.
         * @param parse_only if true, bindings will be generated for the header without passing it to Jextract or cinterop.
         * @param preprocess if true, the KJna will perform C preprocessing on the header before parsing.
         * @param parse_children if the path a header file included during the parsing of this header ends with one of these strings, a binding class will be generated for it.
         */
        fun addHeader(
            header_path: String,
            class_name: String,
            parse_only: Boolean = false,
            preprocess: Boolean = true,
            parse_children: List<String> = emptyList()
        ) {
            headers += listOf(KJnaGeneratePackageHeaderConfiguration(header_path, class_name, parse_only = parse_only, preprocess = preprocess, parse_children = parse_children))
        }

        /**
         * Sets [dev.toastbits.kjna.plugin.KJnaGeneratePackagesConfiguration.Package.disabled_targets] to the list of all build targets except SHARED
         */
        fun disableAllTargets() {
            disabled_targets = KJnaBuildTarget.entries.filter { it != KJnaBuildTarget.SHARED }
        }

        fun jextract(configure: KJnaJextractRuntimeOptions.() -> Unit) {
            configure(jextract_runtime_options)
        }

        fun cinterop(configure: KJnaCinteropRuntimeOptions.() -> Unit) {
            configure(cinterop_runtime_options)
        }
    }

    data class PackageOverrides(
        private var typedef_types: Map<String, String> = emptyMap(),
        private var struct_field_ignored_types: List<String> = emptyList(),
        var anonymous_struct_indices: Map<Int, Int> = emptyMap()
    ): Serializable {

        /**
         * Adds a typedef type override.
         *
         * @param name the name of the typedef.
         * @param the type that the typedef should refer to.
         * @param pointer depth of [type].
         */
        fun overrideTypedefType(name: String, type: CType, pointer_depth: Int = 0) {
            typedef_types = typedef_types.toMutableMap().apply { put(name, json.encodeToString(CValueType(type, pointer_depth))) }
        }

        /**
         * Struct fields with the passed type will be excluded from generated bindings.
         *
         * @param type the type to ignore.
         */
        fun ignoreStructFieldsWithType(type: CType) {
            struct_field_ignored_types = struct_field_ignored_types + listOf(json.encodeToString(type))
        }

        /**
         * Kotlin/Native anonymous structs and unions imported from KJna binding code as 'anonymousStruct<from_index>' will instead be imported as 'anonymousStruct<to_index>'.
         *
         * @param from_index the index of the anonymous struct import to be replaced.
         * @param to_index the index of the anonymous struct to be imported instead of [from_index].
         */
        fun overrideAnonymousStructIndex(from_index: Int, to_index: Int) {
            anonymous_struct_indices = anonymous_struct_indices.toMutableMap().apply{ put(from_index, to_index) }
        }

        companion object {
            private val json: Json get() = Json { useArrayPolymorphism = true }

            internal fun parseTypedefTypes(overrides: PackageOverrides): Map<String, CValueType> =
                overrides.typedef_types.entries.associate { it.key to json.decodeFromString(it.value) }

            internal fun getStructFieldIgnoredTyped(overrides: PackageOverrides): List<CType> =
                overrides.struct_field_ignored_types.map { json.decodeFromString(it) }
        }
    }
}

/**
 * Binding configuration for a single header file.
 * @see dev.toastbits.kjna.plugin.KJnaGeneratePackagesConfiguration.Package.addHeader
 *
 * @property header_path the path to the header file, relative to an include directory.
 * @property class_name the name of the class to generate for this header file.
 * @param parse_only if true, bindings will be generated for the header without passing it to Jextract or cinterop.
 * @param preprocess if true, the KJna will perform C preprocessing on the header before parsing.
 * @param parse_children if the path a header file included during the parsing of this header ends with one of these strings, a binding class will be generated for it.
 * @property override_jextract_loader header-specific override for [dev.toastbits.kjna.plugin.KJnaGenerationOptions.override_jextract_loader].
 */
data class KJnaGeneratePackageHeaderConfiguration(
    var header_path: String,
    var class_name: String,
    var parse_only: Boolean = false,
    var preprocess: Boolean = true,
    var parse_children: List<String> = emptyList(),
    var override_jextract_loader: Boolean? = null
): Serializable

fun KJnaGeneratePackagesConfiguration.Package.addToCompilation(
    compilation: KotlinNativeCompilation,
    def_file_directory: File
): File {
    val def_file: File = def_file_directory.resolve(package_name + "-" + compilation.target.name + ".def")

    compilation.cinterops.apply {
        create(package_name) { cinterop ->
            cinterop.packageName = KJnaBindTargetNativeCinterop.getNativePackageName(package_name)
            cinterop.defFile(def_file)
        }
    }

    return def_file
}

fun KJnaGeneratePackagesConfiguration.Package.createDefFile(def_file: File, parser: CHeaderParser, options: KJnaCinteropRuntimeOptions) {
    if (!def_file.exists()) {
        def_file.ensureParentDirsCreated()
        def_file.createNewFile()
    }

    val compiler_opts: List<String> = include_dirs.map { "-I$it" }
    val linker_opts: List<String> = libraries.map { "-l$it" }
    val header_files: List<String> = options.extra_headers + headers.mapNotNull { if (it.parse_only) null else parser.getHeaderFile(it.header_path).absolutePath }

    def_file.writeText("""
        compilerOpts = ${compiler_opts.joinToString(" ")}
        linkerOpts = ${linker_opts.joinToString(" ")}
        headers = ${header_files.joinToString(" ")}

    """.trimIndent())
}
