package fr.acinq.phoenix.app

import fr.acinq.eclair.WalletParams
import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.blockchain.electrum.HeaderSubscriptionResponse
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.db.SqliteAppDb
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.*
import org.kodein.db.*
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class, ExperimentalStdlibApi::class)
class AppConfigurationManager(
    private val noSqlDb: DB, // TODO to be replaced by [appDb]
    private val appDb: SqliteAppDb,
    private val httpClient: HttpClient,
    private val electrumClient: ElectrumClient,
    private val chain: Chain,
    loggerFactory: LoggerFactory
) : CoroutineScope by MainScope() {

    private val logger = newLogger(loggerFactory)

    private val electrumServerUpdates = ConflatedBroadcastChannel<ElectrumServer>()
    fun openElectrumServerUpdateSubscription(): ReceiveChannel<ElectrumServer> =
        electrumServerUpdates.openSubscription()

    init {
        // Wallet Params
        initWalletParams()
        // Electrum Triggers
        noSqlDb.on<ElectrumServer>().register {
            didPut {
                launch { electrumServerUpdates.send(it) }
            }
        }
        launch {
            electrumClient.openNotificationsSubscription().consumeAsFlow()
                .filterIsInstance<HeaderSubscriptionResponse>().collect { notification ->
                    if (getElectrumServer().blockHeight == notification.height &&
                        getElectrumServer().tipTimestamp == notification.header.time
                    ) return@collect

                    putElectrumServer(
                        getElectrumServer().copy(
                            blockHeight = notification.height,
                            tipTimestamp = notification.header.time
                        )
                    )
                }
        }
    }

    // WalletParams
    private val _walletParams = MutableStateFlow<WalletParams?>(null)
    val walletParams: StateFlow<WalletParams?> = _walletParams

    public fun initWalletParams() = launch {
        // TODO manage startup with conf and timeouts
        val (instant, walletParams) = appDb.getWalletParamsList().filterNotNull().first()[0]
        _walletParams.value = walletParams
    }

    private var updateParametersJob: Job? = null
    public fun startWalletParamsLoop() {
        updateParametersJob = updateWalletParamsLoop()
    }
    public fun stopWalletParamsLoop() {
        launch { updateParametersJob?.cancelAndJoin() }
    }

    @OptIn(ExperimentalTime::class)
    private fun updateWalletParamsLoop() = launch {
        while (isActive) {
            fetchAndStoreWalletParams()
            delay(5.minutes)
        }
    }

    private suspend fun fetchAndStoreWalletParams() {
        try {
            val apiParams = httpClient.get<ApiWalletParams>("https://acinq.co/phoenix/walletcontext.json")
            val newWalletParams = apiParams.export(chain)
            logger.debug { "retrieved WalletParams=${newWalletParams}" }
            appDb.setWalletParams(newWalletParams)
        } catch (t: Throwable) {
            logger.error(t)
        }
    }

    // Electrum
    private val electrumServerKey = noSqlDb.key<ElectrumServer>(0)
    private fun createElectrumConfiguration(): ElectrumServer {
        if (noSqlDb[electrumServerKey] == null) {
            logger.info { "Create ElectrumX configuration" }
            setRandomElectrumServer()
        }
        return noSqlDb[electrumServerKey] ?: error("ElectrumServer must be initialized.")
    }

    fun getElectrumServer(): ElectrumServer = noSqlDb[electrumServerKey] ?: createElectrumConfiguration()

    private fun putElectrumServer(electrumServer: ElectrumServer) {
        logger.info { "Update electrum configuration [$electrumServer]" }
        noSqlDb.put(electrumServerKey, electrumServer)
    }

    fun putElectrumServerAddress(host: String, port: Int, customized: Boolean = false) {
        putElectrumServer(getElectrumServer().copy(host = host, port = port, customized = customized))
    }

    fun setRandomElectrumServer() {
        putElectrumServer(
            when (chain) {
                Chain.MAINNET -> electrumMainnetConfigurations.random()
                Chain.TESTNET -> electrumTestnetConfigurations.random()
                Chain.REGTEST -> platformElectrumRegtestConf()
            }.asElectrumServer()
        )
    }
}
