package dev.toastbits.kjna.runtime

// Runtime class information stored here because the runtime library is compiled for Java 21
open class RuntimeType(
    val name: String,
    val coordinates: String
) {
    object KJnaMemScope: RuntimeType("KJnaMemScope", "dev.toastbits.kjna.runtime.KJnaMemScope")
    object KJnaPointer: RuntimeType("KJnaPointer", "dev.toastbits.kjna.runtime.KJnaPointer")
    object KJnaTypedPointer: RuntimeType("KJnaTypedPointer", "dev.toastbits.kjna.runtime.KJnaTypedPointer") {
        val pointer: String = "pointer"
        val native_scope: String = "native_scope"
    }
    object KJnaAllocationCompanion: RuntimeType("KJnaAllocationCompanion", "dev.toastbits.kjna.runtime.KJnaAllocationCompanion")
    object KJnaNativeStruct: RuntimeType("KJnaNativeStruct", "dev.toastbits.kjna.runtime.KJnaNativeStruct")
    object pointedAs: RuntimeType("pointedAs", "dev.toastbits.kjna.runtime.pointedAs")
}
