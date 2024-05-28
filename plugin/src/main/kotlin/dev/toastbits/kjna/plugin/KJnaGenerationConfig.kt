package dev.toastbits.kjna.plugin

import java.io.File
import org.gradle.api.tasks.*

interface KJnaGenerationConfig {
    @get:Input @get:Optional
    var packages: KJnaGeneratePackagesConfiguration

    @get:Input @get:Optional
    var build_targets: List<KJnaBuildTarget>

    @get:Input @get:Optional
    var include_dirs: List<String>

    @get:OutputDirectory @get:Optional
    val native_def_output_dir: File

    @get:OutputDirectory @get:Optional
    val common_output_dir: File

    @get:OutputDirectory @get:Optional
    val jvm_output_dir: File

    @get:OutputDirectory @get:Optional
    val java_output_dir: File

    @get:OutputDirectory @get:Optional
    val native_output_dir: File
}
