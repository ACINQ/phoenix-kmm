package fr.acinq.phoenix.tor

import fr.acinq.eclair.io.TcpSocket
import fr.acinq.eclair.io.linesFlow
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.getApplicationCacheDirectoryPath
import fr.acinq.phoenix.utils.getTemporaryDirectoryPath
import fr.acinq.phoenix.utils.hmacSha256
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.onReceiveOrNull
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.selects.select
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import org.kodein.memory.file.*
import org.kodein.memory.io.readBytes
import org.kodein.memory.use
import kotlin.random.Random


expect fun startTorInThread(args: Array<String>)

@OptIn(ExperimentalCoroutinesApi::class)
class Tor(private val ctx: PlatformContext, loggerFactory: LoggerFactory) {

    private val logger = newLogger(loggerFactory)

    private val requestChannel = Channel<Pair<TorControlRequest, CompletableDeferred<TorControlResponse>>>()

    private val controlParser = TorControlParser(loggerFactory)

    private val subscribedEvents = listOf("NOTICE", "WARN", "ERR")

    private var torIsRunning = false

    private suspend fun tryConnect(address: String, port: Int, tries: Int): TcpSocket =
        try {
            logger.debug { "Connecting to Tor Control on $address:$port" }
            TcpSocket.Builder().connect(address, port, tls = null)
        } catch (ex: TcpSocket.IOException.ConnectionRefused) {
            if (tries == 0) throw ex
            logger.debug { "Tor control is not ready yet" }
            delay(200)
            tryConnect(address, port, tries - 1)
        }

    private suspend fun tryRead(file: Path): ByteArray {
        val timeWait = 0
        while (file.getType() !is EntityType.File) {
            logger.debug { "${file.name} does not exist yet" }
            if (timeWait >= 5000) {
                error("${file.name} was not created in 5 seconds")
            }
            delay(100)
        }

        return file.openReadableFile().use { it.readBytes() }
    }

    fun start() {
        if (torIsRunning) {
            logger.error { "Cannot start Tor as it is already running!" }
            return
        }

        logger.info { "Starting Tor thread" }

        val portFile = Path(getTemporaryDirectoryPath(ctx)).resolve("tor-control.port")
        val dataDir = Path(getApplicationCacheDirectoryPath(ctx)).resolve("tor_data")

        startTorInThread(arrayOf(
            "tor",
            "__DisableSignalHandlers", "1",
            //"SafeSocks", "1",
            "SocksPort", SOCKS_PORT.toString(),
            "NoExec", "1",
            "ControlPort", "auto",
            "ControlPortWriteToFile", portFile.path,
            "CookieAuthentication", "1",
            "DataDirectory", dataDir.path,
            "Log", "err file /dev/null" // Logs will be monitored by controller
        ))

        GlobalScope.launch(Dispatchers.Main) {
            val portString = tryRead(portFile).decodeToString().trim()
            logger.debug { "Port control file content: $portString" }
            if (!portString.startsWith("PORT=")) error("Invalid port file content: $portString")
            val (address, port) = portString.removePrefix("PORT=").split(":")

            val socket = tryConnect(address, port.toInt(), 15)
            logger.info { "Connected to Tor Control Socket." }

            torIsRunning = true

            val socketRequestChannel = Channel<TorControlRequest>()
            val socketResponseChannel = Channel<TorControlResponse>()

            launch {
                socketRequestChannel.consumeEach { command ->
                    socket.send(command.toProtocolString().encodeToByteArray(), flush = true)
                }
            }

            launch {
                try {
                    socket.linesFlow().collect { line ->
                        controlParser.parseLine(line)?.let { socketResponseChannel.send(it) }
                    }
                } catch (ex: TcpSocket.IOException.ConnectionClosed) {
                    logger.info { "Tor Control Socket disconnected!" }
                } finally {
                    torIsRunning = false
                    socketRequestChannel.close()
                    socketResponseChannel.close()
                    controlParser.reset()
                }
            }

            launch {
                do {
                    val loop = select<Boolean> {
                        (socketResponseChannel.onReceiveOrNull()) { reply ->
                            if (reply == null) false
                            else {
                                if (reply.isAsync) handleAsyncResponse(reply)
                                else logger.warning { "Received a sync reply but did not expect one: $reply" }
                                true
                            }
                        }
                        requestChannel.onReceive { (cmd, def) ->
                            socketRequestChannel.send(cmd)
                            while (true) {
                                val reply = socketResponseChannel.receive()
                                if (reply.isAsync) handleAsyncResponse(reply)
                                else {
                                    def.complete(reply)
                                    break
                                }
                            }
                            true
                        }
                    }
                } while (loop)
            }

            val cookie = tryRead(dataDir.resolve("control_auth_cookie"))
            logger.debug { "Cookie file contains ${cookie.size} bytes" }

            val clientNonce = Random.nextBytes(1)
            val clientNonceHex = Hex.encode(clientNonce).toUpperCase()
            logger.debug { "Auth challenge client nonce: $clientNonceHex" }
            val acResponse = require(TorControlRequest("AUTHCHALLENGE", listOf("SAFECOOKIE", clientNonceHex)))
            val (acCommand, acValues) = acResponse.replies[0].parseCommandKeyValues()
            if (acCommand != "AUTHCHALLENGE") error("Bad auth challenge reply: $acCommand")
            val serverHash = acValues["SERVERHASH"]?.let { Hex.decode(it) }
                ?: error("Auth challenge reply has no SERVERHASH: $acResponse")
            val serverNonce = acValues["SERVERNONCE"]?.let { Hex.decode(it) }
                ?: error("Auth challenge reply has no SERVERNONCE: $acResponse")

            val authMessage = cookie + clientNonce + serverNonce
            if (!serverHash.contentEquals(hmacSha256(SAFECOOKIE_SERVER_KEY, authMessage))) error("Bad auth server hash!")
            logger.debug { "Auth server hash is valid" }

            val clientHash = hmacSha256(SAFECOOKIE_CLIENT_KEY, authMessage)
            require(TorControlRequest("AUTHENTICATE", listOf(Hex.encode(clientHash).toUpperCase())))
            require(TorControlRequest("TAKEOWNERSHIP"))
            require(TorControlRequest("SETEVENTS", subscribedEvents))
        }
    }

    private suspend fun require(request: TorControlRequest): TorControlResponse {
        val response = sendCommand(request)
        if (response.isError) error("${request.command.toLowerCase().capitalize()} error: ${response.status} - ${response.replies[0].reply}")
        logger.debug { "${request.command.toLowerCase().capitalize()} success!" }
        return response
    }

    private fun handleAsyncResponse(response: TorControlResponse) {
        val (event, firstLine) = response.replies[0].reply.split(' ', limit = 2)
        when (event) {
            "NOTICE" -> logger.info { "TOR: $firstLine" }
            "WARN" -> logger.warning { "TOR: $firstLine" }
            "ERR" -> logger.error { "TOR: $firstLine" }
            else -> logger.warning { "Received unknown event $event (${response.replies})" }
        }
    }

    private suspend fun sendCommand(request: TorControlRequest): TorControlResponse {
        val reply = CompletableDeferred<TorControlResponse>()
        requestChannel.send(request to reply)
        return reply.await()
    }

    fun stop() {
        if (!torIsRunning) {
            logger.warning { "Cannot stop Tor as it is not running!" }
            return
        }

        GlobalScope.launch(Dispatchers.Main) {
            sendCommand(TorControlRequest("SIGNAL", listOf("SHUTDOWN")))
        }
    }

    companion object {
        const val SOCKS_PORT = 34781 // CRC-16 of "Phoenix"!
        val SAFECOOKIE_SERVER_KEY = "Tor safe cookie authentication server-to-controller hash".encodeToByteArray()
        val SAFECOOKIE_CLIENT_KEY = "Tor safe cookie authentication controller-to-server hash".encodeToByteArray()
    }
}
