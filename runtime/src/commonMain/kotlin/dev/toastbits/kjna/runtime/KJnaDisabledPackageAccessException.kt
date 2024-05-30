package dev.toastbits.kjna.runtime

class KJnaDisabledPackageAccessException: IllegalStateException() {
    override val message: String = "This package is disabled"
}
