package dev.toastbits.kjna.c

import kotlinx.serialization.Serializable

@Serializable
data class CValueType(
    val type: CType,
    val pointer_depth: Int
)
