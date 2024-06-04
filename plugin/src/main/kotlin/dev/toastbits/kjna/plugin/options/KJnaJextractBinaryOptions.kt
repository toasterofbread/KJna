package dev.toastbits.kjna.plugin.options

import java.io.File
import org.gradle.api.tasks.*

/**
 */
interface KJnaJextractBinaryOptions {
    @get:InputFile @get:Optional
    var jextract_binary: File?

    @get:Input @get:Optional
    var jextract_archive_url: String

    @get:Input @get:Optional
    var jextract_archive_exe_path: String

    @get:OutputDirectory @get:Optional
    var jextract_archive_extract_directory: File
}
