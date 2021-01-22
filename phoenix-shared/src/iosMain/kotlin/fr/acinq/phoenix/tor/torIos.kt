package fr.acinq.phoenix.tor

import fr.acinq.phoenix.tor_in_thread.tor_in_thread
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toCStringArray

actual fun startTorInThread(args: Array<String>) {
    memScoped {
        tor_in_thread(args.size, args.toCStringArray(this))
    }
}
