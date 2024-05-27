package dev.toastbits.kjna.binder

import dev.toastbits.kjna.c.CFunctionDeclaration
import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CValueType

class BinderTargetJvmJextract(): KJnaBinderTarget {
    override fun getClassModifiers(): List<String> = listOf("actual")
    override fun getSourceFileExtension(): String = "jvm.kt"

    override fun implementKotlinFunction(function: CFunctionDeclaration, function_header: String, context: BindingGenerator.GenerationScope): String {
        return "actual $function_header { TODO() }"
    }

    override fun implementKotlinStructConstructor(struct: CType.Struct, context: BindingGenerator.GenerationScope): String? {
        context.importFromCoordinates(MEMORY_SEGMENT_IMPORT)
        return "constructor(val $STRUCT_MEM_PROPERTY_NAME: $MEMORY_SEGMENT_CLASS)"
    }

    override fun implementKotlinStructField(name: String, index: Int, type: CValueType, type_name: String, struct: CType.Struct, context: BindingGenerator.GenerationScope): String {
        return "TODO"
    }

    override fun implementKotlinUnionConstructor(union: CType.Union, name: String, context: BindingGenerator.GenerationScope): String? {
        return null
    }

    override fun implementKotlinUnionField(name: String, index: Int, type: CValueType, type_name: String, union: CType.Union, union_name: String, context: BindingGenerator.GenerationScope): String {
        return "TODO"
    }

    override fun getStructCompanionObject(struct: CType.Struct, context: BindingGenerator.GenerationScope): String? {
        return "TODO"
    }

    companion object {
        const val MEMORY_SEGMENT_CLASS: String = "MemorySegment"
        const val MEMORY_SEGMENT_IMPORT: String = "java.lang.foreign.MemorySegment"
        const val STRUCT_MEM_PROPERTY_NAME: String = "_memory_segment"
    }
}
