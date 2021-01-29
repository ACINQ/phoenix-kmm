package fr.acinq.phoenix.utils

import fr.acinq.tor.Tor
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


fun torLog(loggerFactory: LoggerFactory): (Tor.LogLevel, String) -> Unit {
    val logger = loggerFactory.newLogger(Tor::class)

    return { level, message ->
        when (level) {
            Tor.LogLevel.DEBUG -> logger.debug { message }
            Tor.LogLevel.NOTICE -> logger.info { message }
            Tor.LogLevel.WARN -> logger.warning { message }
            Tor.LogLevel.ERR -> logger.error { message }
        }
    }
}
