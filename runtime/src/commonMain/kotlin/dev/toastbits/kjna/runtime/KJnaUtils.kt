package dev.toastbits.kjna.runtime

expect object KJnaUtils {
    fun getEnv(name: String): String?
    fun setLocale(category: Int, locale: String)
}
