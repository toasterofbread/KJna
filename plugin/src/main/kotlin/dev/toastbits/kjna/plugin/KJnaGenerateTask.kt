package dev.toastbits.kjna.plugin

import org.gradle.api.tasks.*
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import java.io.File
import dev.toastbits.kjna.c.CHeaderParser
import dev.toastbits.kjna.binder.KJnaBinder
import dev.toastbits.kjna.binder.KJnaBinderTarget

abstract class KJnaGenerateTask: DefaultTask() {
    @InputDirectory
    @Optional
    var jvm_source_dir: File? = null

    @InputDirectory
    @Optional
    var native_source_dir: File? = null

    @Input
    @Optional
    var packages: KJnaGeneratePackagesConfiguration = KJnaGeneratePackagesConfiguration()
        set(value) {
            field = value
            configureNativeDefs.packages = value
        }

    @Input
    @Optional
    var include_dirs: List<String> =
        listOf(
            "/usr/include/",
            "/usr/local/include/",
            "/usr/include/linux/"
        )

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

    @OutputDirectory
    val native_def_file_directory: File = project.layout.buildDirectory.dir("kjna-defs").get().asFile.also { it.mkdirs() }

    private val configureNativeDefs: KJnaConfigureNativeDefsTask = project.tasks.register(KJnaConfigureNativeDefsTask.NAME, KJnaConfigureNativeDefsTask::class.java).get()
    private val prepareJextract: KJnaPrepareJextractTask = project.tasks.register(KJnaPrepareJextractTask.NAME, KJnaPrepareJextractTask::class.java).get()

    init {
        description = "TODO"

        configureNativeDefs.native_def_file_directory = native_def_file_directory
        dependsOn(configureNativeDefs)

        dependsOn(prepareJextract)
    }

    fun packages(jvm_target: KotlinJvmTarget?, native_targets: List<KotlinNativeTarget>, configure: KJnaGeneratePackagesConfiguration.() -> Unit) {
        if (jvm_source_dir == null && jvm_target != null) {
            jvm_source_dir = jvm_target.compilations.first().defaultSourceSet.kotlin.sourceDirectories.first()
        }

        val parser: CHeaderParser = CHeaderParser(include_dirs)

        val packages: KJnaGeneratePackagesConfiguration = KJnaGeneratePackagesConfiguration().also { configure(it) }
        this.packages = packages

        val def_files: MutableList<List<File>> = mutableListOf()

        for (pkg in packages.packages) {
            def_files.add(native_targets.map { target ->
                val compilation: KotlinNativeCompilation = target.compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
                pkg.addToCompilation(compilation, parser, native_def_file_directory)
            })
        }

        configureNativeDefs.native_def_files = def_files
    }

    @TaskAction
    fun generateKJnaBindings() {
        val bind_targets: List<KJnaBinderTarget> =
            listOfNotNull(
                KJnaBinderTarget.SHARED,
                jvm_source_dir?.let { KJnaBinderTarget.JVM_JEXTRACT },
                native_source_dir?.let { KJnaBinderTarget.NATIVE_CINTEROP }
            )

        if (bind_targets.isEmpty()) {
            return
        }

        val parser: CHeaderParser = CHeaderParser(include_dirs)
        parser.parse(packages.packages.flatMap { it.headers.map { it.header_path } })

        val header_bindings: List<KJnaBinder.Header> =
            packages.packages.flatMap { pkg ->
                pkg.headers.map { header ->
                    KJnaBinder.Header(
                        class_name = header.class_name,
                        package_name = pkg.package_name,
                        info = parser.getHeaderByInclude(header.header_path)
                    )
                }
            }

        // var jextract: File = prepareJextract.final_jextract_binary

        val binder: KJnaBinder = KJnaBinder(header_bindings, parser.getAllTypedefsMap(), bind_targets)
        binder.generateBindings()

        // executeJextractCommand(jextract, listOf("--help"))
    }

    private fun executeJextractCommand(binary: File, args: List<String>) {
        val process: Process = Runtime.getRuntime().exec((listOf(binary.absolutePath) + args).toTypedArray())

        val result: Int = process.waitFor()
        if (result != 0) {
            throw GradleException("Jextract failed ($result).\nExecutable: $binary\nArgs: $args")
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

