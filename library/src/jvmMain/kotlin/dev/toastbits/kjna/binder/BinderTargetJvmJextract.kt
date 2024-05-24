package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.CFunctionDeclaration

class BinderTargetJvmJextract(): KJnaBinderTarget {
    override fun getClassModifiers(): List<String> = listOf("actual")

    override fun implementKotlinFunction(function: CFunctionDeclaration, function_header: String, context: BinderFileGenerator): String {
        TODO()
    }
}
