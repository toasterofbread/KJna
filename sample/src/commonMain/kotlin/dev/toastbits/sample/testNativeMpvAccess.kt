package dev.toastbits.sample

import kjna.libmpv.MpvClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import dev.toastbits.kjna.runtime.KJnaTypedPointer
import dev.toastbits.kjna.runtime.KJnaFunctionPointer
import kjna.struct.mpv_event
import kjna.struct.mpv_handle

fun testNativeMpvAccess() {
    println("--- testNativeMpvAccess() ---")

    println("Creating mpv instance")
    val mpv: MpvClient = createMpv()
    val handle: KJnaTypedPointer<mpv_handle> = mpv.mpv_create()!!

    val client: KJnaTypedPointer<mpv_handle> = mpv.mpv_create_client(handle, "Name")!!

    println("Setting wakeup callback")
    mpv.mpv_set_wakeup_callback(
        client,
        KJnaFunctionPointer.createDataParamFunction0(),
        KJnaFunctionPointer.getDataParam {
            println("Wakeup callback called")
        }
    )

    runBlocking {
        launch(Dispatchers.IO) {
            delay(3000)
            println("Destroying handle...")
            mpv.mpv_terminate_destroy(handle)
        }

        while (true) {
            delay(1000)

            val ptr: KJnaTypedPointer<mpv_event> = mpv.mpv_wait_event(client, 1.0) ?: continue
            val event: mpv_event = ptr.get()

            println("Got event from client: $event")
        }
    }
}
