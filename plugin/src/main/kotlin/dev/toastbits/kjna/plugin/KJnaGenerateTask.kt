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
import dev.toastbits.kjna.c.PackageGenerationScope
import dev.toastbits.kjna.binder.KJnaBinder
import dev.toastbits.kjna.binder.target.BinderTargetShared
import dev.toastbits.kjna.binder.target.BinderTargetJvmJextract
import dev.toastbits.kjna.binder.target.BinderTargetNativeCinterop
import dev.toastbits.kjna.binder.target.BinderTargetDisabled
import dev.toastbits.kjna.binder.target.KJnaBinderTarget
import javax.inject.Inject

abstract class KJnaGenerateTask: DefaultTask(), KJnaGenerationConfig {
    // Inputs
    override var packages: KJnaGeneratePackagesConfiguration = KJnaGeneratePackagesConfiguration()
    override var build_targets: List<KJnaBuildTarget> = KJnaBuildTarget.entries.toList()
    override var include_dirs: List<String> =
        listOf(
            "/usr/include/",
            "/usr/local/include/",
            "/usr/include/linux/"
        )

    // Outputs
    private val build_dir: File = project.layout.buildDirectory.dir("kjna").get().asFile
    override val common_output_dir: File = build_dir.resolve("src/common").apply { mkdirs() }
    override val jvm_output_dir: File = build_dir.resolve("src/jvm").apply { mkdirs() }
    override val java_output_dir: File = project.file("src/jvmMain/java/kjna").apply { mkdirs() }
    override val native_output_dir: File = build_dir.resolve("src/native").apply { mkdirs() }
    override val native_def_output_dir: File = build_dir.resolve("def").apply { mkdirs() }

    var jextract_binary: File?
        @Internal
        get() = prepareJextract.jextract_binary
        set(value) { prepareJextract.jextract_binary = value }
    var jextract_archive_url: String
        @Internal
        get() = prepareJextract.jextract_archive_url
        set(value) { prepareJextract.jextract_archive_url = value }
    var jextract_archive_dirname: String
        @Internal
        get() = prepareJextract.jextract_archive_dirname
        set(value) { prepareJextract.jextract_archive_dirname = value }
    var jextract_archive_extract_directory: File
        @Internal
        get() = prepareJextract.jextract_archive_extract_directory
        set(value) { prepareJextract.jextract_archive_extract_directory = value }

    @get:Internal
    internal val configureNativeDefs: KJnaConfigureNativeDefsTask = project.tasks.register(KJnaConfigureNativeDefsTask.NAME, KJnaConfigureNativeDefsTask::class.java).get()
    @get:Internal
    internal val prepareJextract: KJnaPrepareJextractTask = project.tasks.register(KJnaPrepareJextractTask.NAME, KJnaPrepareJextractTask::class.java).get()
    @get:Internal
    internal val jextractGenerate: KJnaJextractGenerateTask = project.tasks.register(KJnaJextractGenerateTask.NAME, KJnaJextractGenerateTask::class.java).get()

    init {
        description = "TODO"

        project.afterEvaluate {
            if (build_targets.contains(KJnaBuildTarget.NATIVE)) {
                configureNativeDefs.packages = packages
                configureNativeDefs.include_dirs = include_dirs
                configureNativeDefs.output_directory = native_def_output_dir
                dependsOn(configureNativeDefs)
            }

            if (build_targets.contains(KJnaBuildTarget.JVM)) {
                jextractGenerate.jextract_binary = prepareJextract.getFinalJextractBinaryFile()
                jextractGenerate.packages = packages
                jextractGenerate.include_dirs = include_dirs
                jextractGenerate.output_directory = java_output_dir

                jextractGenerate.dependsOn(prepareJextract)
                dependsOn(jextractGenerate)
            }
        }
    }

    @TaskAction
    fun generateKJnaBindings() {
        val bind_targets: List<KJnaBinderTarget> =
            build_targets.map { target ->
                when (target) {
                    KJnaBuildTarget.SHARED -> KJnaBinderTarget.SHARED
                    KJnaBuildTarget.JVM -> KJnaBinderTarget.JVM_JEXTRACT
                    KJnaBuildTarget.NATIVE -> KJnaBinderTarget.NATIVE_CINTEROP
                }
            }

        if (bind_targets.isEmpty()) {
            return
        }

        val parser: CHeaderParser = CHeaderParser(include_dirs)
        for (pkg in packages.packages) {
            val scope: PackageGenerationScope = PackageGenerationScope()
            parser.parse(pkg.headers.map { it.header_path }, scope)
        }

        val bindings: List<KJnaBinder.GeneratedBindings> =
            packages.packages.map { pkg ->
                val header_bindings: List<KJnaBinder.Header> =
                    pkg.headers.map { header ->
                        KJnaBinder.Header(
                            class_name = header.class_name,
                            package_name = pkg.package_name,
                            info = parser.getHeaderByInclude(header.header_path)
                        )
                    }

                val binder: KJnaBinder = KJnaBinder(pkg.package_name, header_bindings, parser.getAllTypedefsMap())

                if (pkg.enabled) {
                    return@map binder.generateBindings(bind_targets)
                }

                val targets: List<KJnaBinderTarget> = bind_targets.map { if (it is BinderTargetShared) it else KJnaBinderTarget.DISABLED }.distinct()
                val result: KJnaBinder.GeneratedBindings = binder.generateBindings(targets)

                return@map result.copy(
                    files = bind_targets.associateWith { target ->
                        if (target is BinderTargetShared) result.files[target]!!
                        else result.files[KJnaBinderTarget.DISABLED]!!
                    }
                )
            }

        for (target in bind_targets) {
            val target_directory: File =
                when (target) {
                    is BinderTargetShared -> common_output_dir
                    is BinderTargetJvmJextract -> jvm_output_dir
                    is BinderTargetNativeCinterop -> native_output_dir
                    is BinderTargetDisabled -> throw IllegalStateException()
                }.resolve("kotlin")

            if (target_directory.exists()) {
                target_directory.deleteRecursively()
            }

            for (binding in bindings) {
                for ((cls, content) in binding.files[target]!!) {
                    val file: File = target_directory.resolve(cls.replace(".", "/") + '.' + target.getSourceFileExtension())
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

// afterEvaluate {
//     for (task in listOf("compileKotlinJvm", "jvmSourcesJar")) {
//         tasks.getByName(task) {
//             dependsOn(tasks.jextract)
//         }
//     }
// }

