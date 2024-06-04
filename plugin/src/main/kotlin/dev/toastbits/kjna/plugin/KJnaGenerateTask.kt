package dev.toastbits.kjna.plugin

import java.io.File
import org.gradle.api.tasks.*
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
import dev.toastbits.kjna.c.CHeaderParser
import dev.toastbits.kjna.c.CTypedef
import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.PackageGenerationScope
import dev.toastbits.kjna.binder.KJnaBinder
import dev.toastbits.kjna.binder.target.KJnaBindTarget
import dev.toastbits.kjna.binder.target.KJnaBindTargetShared
import dev.toastbits.kjna.binder.target.KJnaBindTargetJvmJextract
import dev.toastbits.kjna.binder.target.KJnaBindTargetNativeCinterop
import dev.toastbits.kjna.binder.target.KJnaBindTargetDisabled
import dev.toastbits.kjna.plugin.options.KJnaGenerationOptions
import dev.toastbits.kjna.plugin.options.KJnaJextractBinaryOptions
import dev.toastbits.kjna.plugin.options.KJnaJextractRuntimeOptions
import dev.toastbits.kjna.plugin.options.KJnaCinteropRuntimeOptions
import javax.inject.Inject
import withIndex

abstract class KJnaGenerateTask: DefaultTask(), KJnaGenerationOptions {
    // Inputs
    override var packages: KJnaGeneratePackagesConfiguration = KJnaGeneratePackagesConfiguration()
    override var build_targets: List<KJnaBuildTarget> = KJnaBuildTarget.DEFAULT_TARGETS
    override var disabled_build_targets: List<KJnaBuildTarget> = emptyList()
    override var include_dirs: List<String> =
        listOf(
            "/usr/include/",
            "C:/msys64/mingw64/include/"
        )
    override var parser_include_dirs: List<String> = emptyList()
    override var override_jextract_loader: Boolean = false

    // Outputs
    private val base_build_dir: File = project.layout.buildDirectory.dir("kjna").get().asFile
    override var source_output_dir: File = base_build_dir.resolve("src").apply { mkdirs() }
    override var java_output_dir: File = project.file("src/jvmMain/java/kjna").apply { mkdirs() }
    override var native_def_output_dir: File = base_build_dir.resolve("def").apply { mkdirs() }

    @Internal
    internal fun getJextractOptions(): KJnaJextractBinaryOptions = prepareJextract
    @Internal
    internal fun getJextractRuntimeOptions(): KJnaJextractRuntimeOptions = jextractGenerate
    @Internal
    internal fun getCinteropRuntimeOptions(): KJnaCinteropRuntimeOptions = configureNativeDefs

    @get:Internal
    private val prepareJextract: KJnaPrepareJextractTask = project.tasks.register(KJnaPrepareJextractTask.NAME, KJnaPrepareJextractTask::class.java).get()
    @get:Internal
    private val jextractGenerate: KJnaJextractGenerateTask = project.tasks.register(KJnaJextractGenerateTask.NAME, KJnaJextractGenerateTask::class.java).get()
    @get:Internal
    private val configureNativeDefs: KJnaConfigureNativeDefsTask = project.tasks.register(KJnaConfigureNativeDefsTask.NAME, KJnaConfigureNativeDefsTask::class.java).get()

    fun setNativeDefFiles(def_files: List<List<File>>) {
        configureNativeDefs.native_def_files = def_files
    }

    init {
        description = "Generate binding files for the configured packages and targets."

        project.afterEvaluate {
            if (build_targets.any { it.isNative() }) {
                configureNativeDefs.packages = packages
                configureNativeDefs.include_dirs = include_dirs
                configureNativeDefs.output_directory = native_def_output_dir
                dependsOn(configureNativeDefs)
            }

            if (build_targets.contains(KJnaBuildTarget.JVM)) {
                jextractGenerate.jextract_binary = prepareJextract.getFinalJextractBinaryFile()
                jextractGenerate.packages = packages
                jextractGenerate.include_dirs = include_dirs
                jextractGenerate.override_jextract_loader = override_jextract_loader
                jextractGenerate.output_directory = java_output_dir

                jextractGenerate.dependsOn(prepareJextract)
                dependsOn(jextractGenerate)
            }
        }
    }

    @TaskAction
    fun generateKJnaBindings() {
        val enabled_targets: List<KJnaBuildTarget> = build_targets.normalised()
        val disabled_targets: List<KJnaBuildTarget> = disabled_build_targets.normalised()

        if (enabled_targets.isEmpty() && disabled_targets.isEmpty()) {
            return
        }

        check(!disabled_targets.contains(KJnaBuildTarget.SHARED)) { "Shared (common) target cannot be disabled as it has no implementation" }

        val targets_intersection: Set<KJnaBuildTarget> = enabled_targets.intersect(disabled_targets)
        check(targets_intersection.isEmpty()) { "The following build targets were specified as both enabled and disabled: ${targets_intersection}" }

        val bind_targets: Map<KJnaBuildTarget, KJnaBindTarget> = (
            enabled_targets.associateWith { target ->
                when (target) {
                    KJnaBuildTarget.SHARED -> KJnaBindTargetShared()
                    KJnaBuildTarget.JVM -> KJnaBindTargetJvmJextract()
                    KJnaBuildTarget.NATIVE_ALL,
                    KJnaBuildTarget.NATIVE_LINUX_X64,
                    KJnaBuildTarget.NATIVE_LINUX_ARM64,
                    KJnaBuildTarget.NATIVE_MINGW_X64 -> KJnaBindTargetNativeCinterop()
                }
            }
            + disabled_targets.associateWith { KJnaBindTargetDisabled() }
        )

        val parser: CHeaderParser = CHeaderParser((include_dirs + parser_include_dirs).distinct())
        for (pkg in packages.packages) {
            println("Parsing headers for $pkg...")

            val package_scope: PackageGenerationScope = PackageGenerationScope(parser)
            parser.parse(pkg.headers.mapNotNull { if (!it.preprocess) null else it.header_path }, package_scope, pkg.include_dirs, pkg.parser_ignore_headers)
        }

        val package_bindings: List<KJnaBinder.GeneratedBindings> =
            packages.packages.map { pkg ->
                check(!pkg.disabled_targets.contains(KJnaBuildTarget.SHARED)) { "Shared (common) target cannot be disabled on package $pkg" }

                val header_bindings: List<KJnaBinder.Header> =
                    pkg.headers.map { header ->
                        KJnaBinder.Header(
                            class_name = header.class_name,
                            package_name = pkg.package_name,
                            info = parser.getHeaderByInclude(header.header_path)
                        )
                    }

                val struct_field_ignored_types: List<CType> = KJnaGeneratePackagesConfiguration.PackageOverrides.getStructFieldIgnoredTyped(pkg.overrides)

                val typedefs: MutableMap<String, CTypedef> = parser.getAllTypedefsMap().toMutableMap()
                for ((typedef, type) in KJnaGeneratePackagesConfiguration.PackageOverrides.parseTypedefTypes(pkg.overrides)) {
                    typedefs[typedef] = CTypedef(typedef, type)
                }

                val binder: KJnaBinder =
                    object :  KJnaBinder(pkg.package_name, header_bindings, typedefs) {
                        override fun shouldIncludeStructField(name: String, type: CType, struct: CType.Struct): Boolean =
                            !struct_field_ignored_types.contains(type)
                    }

                val package_targets: List<KJnaBindTarget> =
                    bind_targets.entries.map { (build_target, bind_target) ->
                        if (!pkg.enabled || pkg.disabled_targets.contains(build_target)) KJnaBindTargetDisabled() else bind_target
                    }

                return@map binder.generateBindings(package_targets)
            }

        for ((target_index, build_target, bind_target) in bind_targets.withIndex()) {
            val target_directory: File = build_target.getSourceDirectory(source_output_dir)

            if (target_directory.exists()) {
                target_directory.deleteRecursively()
            }

            for (binding in package_bindings) {
                for ((cls, content) in binding.files[target_index]) {
                    val file: File = target_directory.resolve(cls.replace(".", "/") + '.' + build_target.getSourceFileExtension())
                    if (!file.exists()) {
                        file.ensureParentDirsCreated()
                        file.createNewFile()
                    }
                    file.writeText(content)
                }
            }
        }
    }

    private fun KotlinTarget.getSourceDir(): String =
        compilations.first().allKotlinSourceSets.map { it.kotlin.sourceDirectories.toList().toString() }.toString()

    companion object {
        const val NAME: String = "generateKJnaBindings"
    }
}
