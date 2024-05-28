import org.gradle.api.Project
import dev.toastbits.kjna.plugin.*
import dev.toastbits.kjna.c.CHeaderParser
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import java.io.File

class KJnaConfiguration(
    internal val project: Project,
    internal val generate_task: KJnaGenerateTask,
    internal val kotlin: KotlinMultiplatformExtension
) {
    fun generate(configure: KJnaGenerateConfiguration.() -> Unit) {
        configure(KJnaGenerateConfiguration(this))

        kotlin.targets.withType(KotlinJvmTarget::class.java).all { it.withJava() }

        project.afterEvaluate {
            kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(generate_task.common_output_dir)
            kotlin.sourceSets.getByName("jvmMain").kotlin.srcDir(generate_task.jvm_output_dir)
            kotlin.sourceSets.getByName("nativeMain").kotlin.srcDir(generate_task.native_output_dir)
        }
    }
}

class KJnaGenerateConfiguration(
    val config: KJnaConfiguration,
): KJnaGenerationConfig by config.generate_task {
    fun packages(native_targets: List<KotlinNativeTarget> = emptyList(), configure: KJnaGeneratePackagesConfiguration.() -> Unit) {
        val packages: KJnaGeneratePackagesConfiguration = KJnaGeneratePackagesConfiguration().also { configure(it) }
        this.packages = packages

        val def_files: MutableList<List<File>> = mutableListOf()

        for (pkg in packages.packages) {
            def_files.add(native_targets.map { target ->
                val compilation: KotlinNativeCompilation = target.compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
                pkg.addToCompilation(compilation, native_def_output_dir)
            })
        }

        config.generate_task.configureNativeDefs.native_def_files = def_files
    }
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // For KotlinMultiplatformExtension.project
fun KotlinMultiplatformExtension.kjna(configure: KJnaConfiguration.() -> Unit) {
    val generate_task: KJnaGenerateTask = project.tasks.getByName(KJnaGenerateTask.NAME) as KJnaGenerateTask
    configure(KJnaConfiguration(project, generate_task, this))
}
