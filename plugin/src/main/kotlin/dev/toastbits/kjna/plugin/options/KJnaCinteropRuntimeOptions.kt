package dev.toastbits.kjna.plugin.options

import org.gradle.api.tasks.*
import java.io.Serializable

/**
 * @property headers a list of additional headers to be included in Kotlin/Native cinterop generations configured by KJna.
 */
interface KJnaCinteropRuntimeOptions {
    @get:Input
    var extra_headers: List<String>
}

data class KJnaCinteropRuntimeOptionsImpl(
    override var extra_headers: List<String> = emptyList()
): KJnaCinteropRuntimeOptions, Serializable {
    internal fun combine(other: KJnaCinteropRuntimeOptions): KJnaCinteropRuntimeOptionsImpl =
        KJnaCinteropRuntimeOptionsImpl(
            extra_headers + other.extra_headers
        )
}
