import org.gradle.api.Project
import dev.toastbits.kjna.plugin.*
import dev.toastbits.kjna.plugin.options.KJnaGenerationOptions
import dev.toastbits.kjna.plugin.options.KJnaJextractBinaryOptions
import dev.toastbits.kjna.plugin.options.KJnaJextractRuntimeOptions
import dev.toastbits.kjna.c.CHeaderParser
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import org.jetbrains.kotlin.konan.target.Architecture
import java.io.File

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // For KotlinMultiplatformExtension.project
fun KotlinMultiplatformExtension.kjna(configure: KJnaConfiguration.() -> Unit) {
    val generate_task: KJnaGenerateTask = project.tasks.getByName(KJnaGenerateTask.NAME) as KJnaGenerateTask
    configure(KJnaConfiguration(project, generate_task, this))
}

class KJnaConfiguration(
    internal val project: Project,
    internal val generate_task: KJnaGenerateTask,
    internal val kotlin: KotlinMultiplatformExtension
) {
    fun generate(configure: KJnaGenerateConfiguration.() -> Unit) {
        configure(KJnaGenerateConfiguration(this))

        kotlin.targets.withType(KotlinJvmTarget::class.java).all { task ->
            task.withJava()
        }

        project.tasks.matching {
            it is KotlinCompilationTask<*> || it is CInteropProcess || it.name.lowercase().endsWith("sourcesjar")
        }.all { task ->
            task.dependsOn(generate_task)
        }

        project.afterEvaluate {
            for (target in generate_task.build_targets + generate_task.disabled_build_targets) {
                val set_name: String = target.getName() + "Main"
                kotlin.sourceSets.findByName(set_name)?.kotlin?.srcDir(target.getSourceDirectory(generate_task.source_output_dir))
            }
        }
    }
}

class KJnaGenerateConfiguration(
    val config: KJnaConfiguration
): KJnaGenerationOptions by config.generate_task {
    fun packages(native_targets: List<KotlinNativeTarget> = emptyList(), configure: KJnaGeneratePackagesConfiguration.() -> Unit) {
        configure(packages)

        val def_files: MutableList<List<Pair<File, KJnaGenerationOptions.Arch>>> = mutableListOf()

        for (pkg in packages.packages) {
            def_files.add(native_targets.map { target ->
                val arch: KJnaGenerationOptions.Arch =
                    when (target.konanTarget.architecture) {
                        Architecture.X64 -> KJnaGenerationOptions.Arch.x86_64
                        Architecture.ARM64 -> KJnaGenerationOptions.Arch.arm64
                        else -> throw NotImplementedError("Unknown architecture ${target.konanTarget.architecture}")
                    }
                val compilation: KotlinNativeCompilation = target.compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
                return@map Pair(pkg.addToCompilation(compilation, native_def_output_dir), arch)
            })
        }

        config.generate_task.setNativeDefFiles(def_files)
    }

    fun jextract(configure: KJnaJextractConfiguration.() -> Unit) {
        val configuration: KJnaJextractConfiguration = KJnaJextractConfiguration(
            config.generate_task.getJextractOptions(),
            config.generate_task.getJextractRuntimeOptions()
        )
        configure(configuration)
    }
}

class KJnaJextractConfiguration(
    val binary: KJnaJextractBinaryOptions,
    val runtime: KJnaJextractRuntimeOptions
): KJnaJextractBinaryOptions by binary, KJnaJextractRuntimeOptions by runtime

