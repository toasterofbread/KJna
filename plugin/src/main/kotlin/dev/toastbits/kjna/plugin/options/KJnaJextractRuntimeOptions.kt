package dev.toastbits.kjna.plugin.options

import org.gradle.api.tasks.*
import java.io.Serializable

/**
 * @property symbol_filter a list of regular expressions. Symbols not matching at least one of these will be excluded.
 * @property macros a list of C preprocessor macros to pass to Jextract in the format <macro>=<value>. <macro> on its own will be set to 1.
 */
interface KJnaJextractRuntimeOptions {
    @get:Input
    var symbol_filter: List<String>

    @get:Input
    var macros: List<String>

    @get:Input
    var args: List<String>
}

data class KJnaJextractRuntimeOptionsImpl(
    override var symbol_filter: List<String> = emptyList(),
    override var macros: List<String> = emptyList(),
    override var args: List<String> = emptyList()
): KJnaJextractRuntimeOptions, Serializable {
    internal fun combine(other: KJnaJextractRuntimeOptions): KJnaJextractRuntimeOptionsImpl =
        KJnaJextractRuntimeOptionsImpl(
            symbol_filter + other.symbol_filter,
            macros + other.macros,
            args + other.args
        )
}
