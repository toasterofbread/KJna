package dev.toastbits.kjna.runtime

@RequiresOptIn(message = "Some of this function's parameters are function pointers with no detected user data parameter. These pointers will not be passed on to the called function.")
@Target(AnnotationTarget.FUNCTION)
annotation class KJnaUnimplementedFunctionPointer
