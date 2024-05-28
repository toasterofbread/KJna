package dev.toastbits.kjna.binder.target

import dev.toastbits.kjna.c.CFunctionDeclaration
import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CValueType
import dev.toastbits.kjna.binder.BindingGenerator

class BinderTargetJvmJextract(): KJnaBinderTarget {
    override fun getClassModifiers(): List<String> = listOf("actual")
    override fun getSourceFileExtension(): String = "jvm.kt"

    override fun implementFunction(function: CFunctionDeclaration, function_header: String, context: BindingGenerator.GenerationScope): String {
        return "actual $function_header { TODO() }"
    }

    override fun implementStructConstructor(struct: CType.Struct, context: BindingGenerator.GenerationScope): String? {
        context.importFromCoordinates(MEMORY_SEGMENT_IMPORT)
        return "constructor(val $STRUCT_MEM_PROPERTY_NAME: $MEMORY_SEGMENT_CLASS)"
    }

    override fun implementStructField(name: String, index: Int, type: CValueType, type_name: String, struct: CType.Struct, context: BindingGenerator.GenerationScope): String {
        return "TODO"
    }

    override fun implementUnionConstructor(union: CType.Union, name: String, context: BindingGenerator.GenerationScope): String? {
        return null
    }

    override fun implementUnionField(name: String, index: Int, type: CValueType, type_name: String, union: CType.Union, union_name: String, context: BindingGenerator.GenerationScope): String {
        return "TODO"
    }

    override fun implementStructCompanionObject(struct: CType.Struct, context: BindingGenerator.GenerationScope): String? {
        return "TODO()"
    }

    companion object {
        const val MEMORY_SEGMENT_CLASS: String = "MemorySegment"
        const val MEMORY_SEGMENT_IMPORT: String = "java.lang.foreign.MemorySegment"
        const val STRUCT_MEM_PROPERTY_NAME: String = "_memory_segment"
    }
}
