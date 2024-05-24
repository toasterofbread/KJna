package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.CFunctionDeclaration

class BinderTargetShared(): KJnaBinderTarget {
    override fun getClassModifiers(): List<String> = listOf("expect")

    override fun implementKotlinFunction(function: CFunctionDeclaration, function_header: String, context: BinderFileGenerator): String {
        return function_header
    }
}
