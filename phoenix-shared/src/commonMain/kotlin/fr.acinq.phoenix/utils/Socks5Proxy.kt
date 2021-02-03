package fr.acinq.phoenix.utils

import fr.acinq.eclair.io.TcpSocket
import fr.acinq.eclair.io.receiveFully
import fr.acinq.eclair.io.send
import fr.acinq.tor.Tor
import fr.acinq.tor.socks.socks5Handshake
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


class Socks5Proxy(
    private val socketBuilder: TcpSocket.Builder,
    loggerFactory: LoggerFactory,
    private val proxyHost: String, private val proxyPort: Int
    ) : TcpSocket.Builder {

    val logger = newLogger(loggerFactory)

    override suspend fun connect(host: String, port: Int, tls: TcpSocket.TLS?): TcpSocket {
        val socket = socketBuilder.connect(proxyHost, proxyPort)

        val (cHost, cPort) = socks5Handshake(
            host, port,
            receive = { socket.receiveFully(it) },
            send = { socket.send(it, flush = true) }
        )

        logger.debug { "Connected through socks5 to $cHost:$cPort" }

        if (tls != null) return socket.startTls(tls)

        return socket
    }
}

fun TcpSocket.Builder.torProxy(loggerFactory: LoggerFactory) =
    Socks5Proxy(this, loggerFactory, Tor.SOCKS_ADDRESS, Tor.SOCKS_PORT)
