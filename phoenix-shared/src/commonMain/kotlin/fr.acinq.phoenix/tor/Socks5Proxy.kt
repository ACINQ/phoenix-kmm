package fr.acinq.phoenix.tor

import fr.acinq.bitcoin.crypto.Pack
import fr.acinq.eclair.io.TcpSocket
import fr.acinq.eclair.io.receiveFully
import fr.acinq.secp256k1.Hex
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


class Socks5Proxy(
    val socketBuilder: TcpSocket.Builder,
    loggerFactory: LoggerFactory,
    val proxyHost: String, val proxyPort: Int
    ) : TcpSocket.Builder {

    val logger = newLogger(loggerFactory)

    override suspend fun connect(host: String, port: Int, tls: TcpSocket.TLS?): TcpSocket {
        val socket = socketBuilder.connect(proxyHost, proxyPort)

        socket.send(
            byteArrayOf(
                0x05, // Socks version
                0x01, // One auth method supported
                0x00 // Auth method: no authentication
            ),
            flush = true
        )

        val handshake = socket.receiveFully(2)
        check(handshake[0].toInt() == 0x05) { "Server responded by version: 0x${handshake[0].toString(16)}" }

        when (handshake[1].toInt()) {
            0x00 -> {}
            else -> error("Unsupported authentication method: 0x${handshake[1].toString(16)}")
        }

        val destinationHostBytes = host.encodeToByteArray()
        val destinationPortBytes = Pack.writeInt16BE(port.toShort())

        socket.send(
            byteArrayOf(
                0x05, // Socks Version
                0x01, // Command: connect
                0x00, // Reserved
                0x03, // Address type: domain name
                destinationHostBytes.size.toByte()
            ) +
                    destinationHostBytes +
                    destinationPortBytes,
            flush = true
        )

        val response = socket.receiveFully(4)
        check(response[0].toInt() == 0x05) { "Server responded by version: 0x${response[0].toString(16)}" }

        when (response[1].toInt()) {
            0x00 -> {}
            0x01 -> throw TcpSocket.IOException.Unknown("General SOCKS server failure")
            0x02 -> throw TcpSocket.IOException.Unknown("Connection not allowed by ruleset")
            0x03 -> { logger.warning { "Socks5: Network unreachable" } ; throw TcpSocket.IOException.ConnectionRefused() }
            0x04 -> { logger.warning { "Socks5: Host unreachable" } ; throw TcpSocket.IOException.ConnectionRefused() }
            0x05 -> { throw TcpSocket.IOException.ConnectionRefused() }
            0x06 -> throw TcpSocket.IOException.Unknown("TTL expired")
            0x07 -> error("Command not supported")
            0x08 -> error("Address type not supported")
            else -> error("Unknown Socks5 error: 0x${response[1].toString(16)}")
        }

        // response[2] is reserved and ignored.

        val connectedHost = when (response[3].toInt()) {
            0x01 -> {
                val ip = socket.receiveFully(4)
                "${ip[0]}.${ip[1]}.${ip[2]}.${ip[3]}"
            }
            0x03 -> {
                val length = socket.receiveFully(1)[0].toInt()
                socket.receiveFully(length).decodeToString()
            }
            0x04 -> {
                val ip = socket.receiveFully(16)
                Hex.encode(ip).chunked(2).joinToString(":")
            }
            else -> error("Unsupported address type: 0x${response[3].toString(16)}")
        }

        val connectedPort = Pack.int16BE(socket.receiveFully(2)).toInt()

        logger.info { "Socks5: Connected to $connectedHost:$connectedPort" }

        return socket
    }

}
