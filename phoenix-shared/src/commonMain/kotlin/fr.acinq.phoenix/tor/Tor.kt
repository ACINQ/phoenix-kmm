package fr.acinq.phoenix.tor

import fr.acinq.eclair.io.TcpSocket
import fr.acinq.eclair.io.linesFlow
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.getApplicationCacheDirectoryPath
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.onReceiveOrNull
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.selects.select
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


expect fun startTorInThread(args: Array<String>)

@OptIn(ExperimentalCoroutinesApi::class)
class Tor(private val ctx: PlatformContext, loggerFactory: LoggerFactory) {

    private val logger = newLogger(loggerFactory)

    private val requestChannel = Channel<Pair<TorControlRequest, CompletableDeferred<TorControlResponse>>>()

    private val controlParser = TorControlParser(loggerFactory)

    private val subscribedEvents = listOf("NOTICE", "WARN", "ERR")

    private var torIsRunning = false

    private suspend fun tryConnect(port: Int, tries: Int): TcpSocket =
        try {
            TcpSocket.Builder().connect("localhost", port, tls = null)
        } catch (ex: TcpSocket.IOException.ConnectionRefused) {
            if (tries == 0) throw ex
            delay(200)
            tryConnect(port, tries - 1)
        }

    fun start() {
        if (torIsRunning) {
            logger.error { "Cannot start Tor as it is already running!" }
            return
        }

        logger.info { "Starting Tor thread" }

        startTorInThread(arrayOf(
            "tor",
            "__DisableSignalHandlers", "1",
            //"SafeSocks", "1",
            "SocksPort", SOCKS_PORT.toString(),
            "NoExec", "1",
            "ControlPort", "42841", // should be auto
            "CookieAuthentication", "0",
            //"ControlPortWriteToFile", "???",
            "DataDirectory", "${getApplicationCacheDirectoryPath(ctx)}/tor_data",
            "Log", "err file /dev/null" // Logs will be monitored by controller
        ))

        GlobalScope.launch(Dispatchers.Main) {
            val socket = tryConnect(42841, 15)
            logger.info { "Connected to Tor Control Socket." }

            torIsRunning = true

            val socketRequestChannel = Channel<TorControlRequest>()
            val socketResponseChannel = Channel<TorControlResponse>()

            launch {
                socketRequestChannel.consumeEach { command ->
                    socket.send(command.toProtocolString().encodeToByteArray(), flush = true)
                }
                println("COROUTINE socketRequestChannel ENDED!")
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
                println("COROUTINE socket.linesFlow ENDED!")
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
                println("COROUTINE select ENDED!")
            }

            require(TorControlRequest("AUTHENTICATE", listOf("\"\"")))
            require(TorControlRequest("TAKEOWNERSHIP"))
            require(TorControlRequest("SETEVENTS", subscribedEvents))
        }
    }

    private suspend fun require(request: TorControlRequest) {
        val response = sendCommand(request)
        if (response.isError) error("Tor Control ${request.command.toLowerCase()} error: ${response.status} - ${response.replies[0].reply}")
        logger.info { "Tor Control ${request.command.toLowerCase()} success!" }
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
    }
}
