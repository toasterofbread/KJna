package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.CFunctionDeclaration

interface KJnaBinderTarget {
    fun getClassModifiers(): List<String> = emptyList()
    fun implementKotlinFunction(function: CFunctionDeclaration, function_header: String, context: BinderFileGenerator): String

    companion object {
        val SHARED: KJnaBinderTarget = BinderTargetShared()
        val JVM_JEXTRACT: KJnaBinderTarget = BinderTargetJvmJextract()
        val NATIVE_CINTEROP: KJnaBinderTarget = BinderTargetNativeCinterop()
    }
}
