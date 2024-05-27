package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.CFunctionDeclaration
import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CValueType

sealed interface KJnaBinderTarget {
    fun getClassModifiers(): List<String> = emptyList()
    fun getSourceFileExtension(): String = "kt"

    fun implementKotlinFunction(function: CFunctionDeclaration, function_header: String, context: BindingGenerator.GenerationScope): String

    fun implementKotlinStructConstructor(struct: CType.Struct, context: BindingGenerator.GenerationScope): String?
    fun implementKotlinStructField(name: String, index: Int, type: CValueType, type_name: String, struct: CType.Struct, context: BindingGenerator.GenerationScope): String

    fun implementKotlinUnionConstructor(union: CType.Union, name: String, context: BindingGenerator.GenerationScope): String?
    fun implementKotlinUnionField(name: String, index: Int, type: CValueType, type_name: String, union: CType.Union, union_name: String, context: BindingGenerator.GenerationScope): String

    fun getStructCompanionObject(struct: CType.Struct, context: BindingGenerator.GenerationScope): String? = null

    fun getEnumFileContent(enm: CType.Enum, context: BindingGenerator.GenerationScope): String? = null

    companion object {
        val SHARED: KJnaBinderTarget = BinderTargetShared()
        val JVM_JEXTRACT: KJnaBinderTarget = BinderTargetJvmJextract()
        val NATIVE_CINTEROP: KJnaBinderTarget = BinderTargetNativeCinterop()
    }
}
