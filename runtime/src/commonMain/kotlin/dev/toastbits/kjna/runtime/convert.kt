package dev.toastbits.kjna.runtime

fun UByte.convert(): Char = toByte().toInt().toChar()
fun Byte.convert(): Char = toInt().toChar()
fun Char.convert(): Byte = toByte()
