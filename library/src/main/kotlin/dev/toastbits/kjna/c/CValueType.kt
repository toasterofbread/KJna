package dev.toastbits.kjna.c

data class CValueType(
    val type: CType,
    val pointer_depth: Int
)
