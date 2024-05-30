package dev.toastbits.kjna.c

import kotlinx.serialization.Serializable

@Serializable
data class CFunctionParameter(
    val name: String?,
    val type: CValueType
)
