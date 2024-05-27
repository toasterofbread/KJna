package dev.toastbits.kjna.runtime

import java.lang.foreign.Arena

actual class KJnaMemScope {
    private val arena: Arena = Arena.ofConfined()

    actual fun close() {
        arena.close()
    }

    actual inline fun <reified T: Any> alloc(): KJnaTypedPointer<T> {
        TODO()
    }

    actual companion object {
        actual inline fun <T> confined(action: KJnaMemScope.() -> T): T {
            val scope: KJnaMemScope = KJnaMemScope()
            try {
                return action(scope)
            }
            finally {
                scope.close()
            }
        }
    }
}
