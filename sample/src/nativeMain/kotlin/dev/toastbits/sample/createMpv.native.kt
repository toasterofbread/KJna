package dev.toastbits.sample

import kjna.libmpv.MpvClient

actual fun createMpv(): MpvClient = MpvClient()