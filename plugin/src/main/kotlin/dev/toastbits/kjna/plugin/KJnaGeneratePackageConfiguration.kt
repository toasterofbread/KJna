package dev.toastbits.kjna.plugin

import java.io.Serializable
import java.io.File

data class KJnaGeneratePackageConfiguration(
    var package_name: String,
    var output_directory: File? = null,
    var headers: Map<String, String> = emptyMap(),
    var libraries: List<String> = emptyList()
): Serializable {
    fun addHeader(header_path: String, class_name: String) {
        headers = headers.toMutableMap().also { it[class_name] = header_path }
    }
}
