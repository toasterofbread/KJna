package dev.toastbits.kjna.runtime

// Runtime class information stored here because the runtime library is compiled for Java 21
open class RuntimeType(
    val name: String,
    val coordinates: String
) {
    object KJnaDisabledPackageAccessException: RuntimeType("KJnaDisabledPackageAccessException", "dev.toastbits.kjna.runtime.KJnaDisabledPackageAccessException")
    object KJnaMemScope: RuntimeType("KJnaMemScope", "dev.toastbits.kjna.runtime.KJnaMemScope") {
        val confined: String = "confined"
        val native_scope: String = "native_scope"
        val jvm_arena: String = "jvm_arena"
    }
    object KJnaPointer: RuntimeType("KJnaPointer", "dev.toastbits.kjna.runtime.KJnaPointer")
    object KJnaTypedPointer: RuntimeType("KJnaTypedPointer", "dev.toastbits.kjna.runtime.KJnaTypedPointer") {
        val pointer: String = "pointer"
    }
    object KJnaAllocationCompanion: RuntimeType("KJnaAllocationCompanion", "dev.toastbits.kjna.runtime.KJnaAllocationCompanion") {
        val registerAllocationCompanion: String = "registerAllocationCompanion"
    }
    object KJnaVarargList: RuntimeType("KJnaVarargList", "dev.toastbits.kjna.runtime.KJnaVarargList")
    object KJnaFunction: RuntimeType("KJnaFunction", "dev.toastbits.kjna.runtime.KJnaFunction")

    object convert: RuntimeType("convert", "dev.toastbits.kjna.runtime.convert")

    // Native
    object KJnaNativeStruct: RuntimeType("KJnaNativeStruct", "dev.toastbits.kjna.runtime.KJnaNativeStruct")
    object KJnaUnimplementedFunctionPointer: RuntimeType("KJnaUnimplementedFunctionPointer", "dev.toastbits.kjna.runtime.KJnaUnimplementedFunctionPointer")
    object pointedAs: RuntimeType("pointedAs", "dev.toastbits.kjna.runtime.pointedAs")

    // JVM
    object memorySegment: RuntimeType("memorySegment", "dev.toastbits.kjna.runtime.memorySegment")
    object getString: RuntimeType("getString", "dev.toastbits.kjna.runtime.getString")
    object FunctionWrapper: RuntimeType("FunctionWrapper", "dev.toastbits.kjna.runtime.FunctionWrapper") {
        val invoke: String = "invoke"
    }
}
