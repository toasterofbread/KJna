package dev.toastbits.kjna.binder.target

import dev.toastbits.kjna.c.CFunctionDeclaration
import dev.toastbits.kjna.c.CType
import dev.toastbits.kjna.c.CValueType
import dev.toastbits.kjna.binder.BindingGenerator
import dev.toastbits.kjna.binder.KJnaBinder

sealed interface KJnaBindTarget {
    fun getClassModifiers(): List<String> = emptyList()

    fun implementFunction(function: CFunctionDeclaration, function_header: String, header_class_name: String, context: BindingGenerator.GenerationScope): String

    fun implementHeaderConstructor(context: BindingGenerator.GenerationScope): String? = null
    fun implementHeaderInitialiser(all_structs: List<CType.Struct>?, context: BindingGenerator.GenerationScope): String? = null

    fun implementStructConstructor(struct: CType.Struct, name: String, context: BindingGenerator.GenerationScope): String? = null
    fun implementStructField(name: String, index: Int, type: CValueType, type_name: String, struct: CType.Struct, scope_name: String?, context: BindingGenerator.GenerationScope): String
    fun implementStructToStringMethod(struct: CType.Struct, context: BindingGenerator.GenerationScope): String? = null
    fun implementStructCompanionObject(struct: CType.Struct, context: BindingGenerator.GenerationScope): String? = null
    fun implementStructAnnotation(struct: CType.Struct, context: BindingGenerator.GenerationScope): String? = null

    fun implementUnionConstructor(union: CType.Union, name: String, context: BindingGenerator.GenerationScope): String?
    fun implementUnionField(name: String, index: Int, type: CValueType, type_name: String, union: CType.Union, union_name: String, union_field_name: String?, scope_name: String?, context: BindingGenerator.GenerationScope): String

    fun implementEnumFileContent(enm: CType.Enum, context: BindingGenerator.GenerationScope): String? = null
}
