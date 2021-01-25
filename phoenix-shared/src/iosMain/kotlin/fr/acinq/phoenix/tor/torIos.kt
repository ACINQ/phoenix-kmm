package fr.acinq.phoenix.tor

import fr.acinq.phoenix.tor_in_thread.tor_in_thread_start
import fr.acinq.phoenix.tor_in_thread.tor_in_thread_get_is_running
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toCStringArray

actual fun startTorInThread(args: Array<String>) {
    memScoped {
        tor_in_thread_start(args.size, args.toCStringArray(this))
    }
}

actual fun isTorInThreadRunning(): Boolean =
    tor_in_thread_get_is_running() != 0
