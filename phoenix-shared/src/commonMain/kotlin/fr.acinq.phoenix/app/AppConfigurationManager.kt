package fr.acinq.phoenix.app

import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.blockchain.electrum.HeaderSubscriptionResponse
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.ServerAddress
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.db.SqliteAppDb
import fr.acinq.phoenix.utils.RETRY_DELAY
import fr.acinq.phoenix.utils.increaseDelay
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.math.max
import kotlin.time.*

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class, ExperimentalStdlibApi::class)
class AppConfigurationManager(
    private val appDb: SqliteAppDb,
    private val httpClient: HttpClient,
    private val electrumClient: ElectrumClient,
    private val chain: Chain,
    loggerFactory: LoggerFactory
) : CoroutineScope by MainScope() {

    private val logger = newLogger(loggerFactory)

    init {
        initWalletContext()
        watchElectrumMessages()
    }

    private val currentWalletContextVersion = WalletContext.Version.V0

    private val _chainContext = MutableStateFlow<WalletContext.V0.ChainContext?>(null)
    val chainContext: StateFlow<WalletContext.V0.ChainContext?> = _chainContext

    private fun initWalletContext() = launch {
        val (instant, fallbackWalletContext) = appDb.getWalletContextOrNull(currentWalletContextVersion)

        val freshness = Clock.System.now() - instant
        logger.info { "local WalletContext loaded, not updated since=$freshness" }

        val timeout =
            if (freshness < 48.hours) 2.seconds
            else max(freshness.inDays.toInt(), 5) * 2.seconds // max=10s

        // TODO are we using TOR? -> increase timeout

        val walletContext = try {
            withTimeout(timeout) {
                fetchAndStoreWalletContext() ?: fallbackWalletContext
            }
        } catch (t: TimeoutCancellationException) {
            logger.warning { "Unable to fetch WalletContext, using fallback values=$fallbackWalletContext" }
            fallbackWalletContext
        }

        // _chainContext can be updated by [updateWalletContextLoop] before we reach this block.
        // In that case, we don't update from here
        if (_chainContext.value == null) {
            val chainContext = walletContext?.export(chain)
            logger.debug { "init ChainContext=$chainContext" }
            _chainContext.value = chainContext

        }

        logger.info { "chainContext=$chainContext" }
    }

    private var updateWalletContextJob: Job? = null
    public fun startWalletContextLoop() {
        updateWalletContextJob = updateWalletContextLoop()
    }

    public fun stopWalletContextLoop() {
        launch { updateWalletContextJob?.cancelAndJoin() }
    }

    @OptIn(ExperimentalTime::class)
    private fun updateWalletContextLoop() = launch {
        var retryDelay = RETRY_DELAY

        while (isActive) {
            val walletContext = fetchAndStoreWalletContext()
            if (walletContext != null) {
                val chainContext = walletContext.export(chain)
                logger.debug { "update ChainContext=$chainContext" }
                _chainContext.value = chainContext
                retryDelay = 60.minutes
            } else {
                retryDelay = increaseDelay(retryDelay)
            }

            delay(retryDelay)
        }
    }

    private suspend fun fetchAndStoreWalletContext(): WalletContext.V0? {
        return try {
            val rawData = httpClient.get<String>("https://acinq.co/phoenix/walletcontext.json")
            appDb.setWalletContext(currentWalletContextVersion, rawData)
        } catch (t: Throwable) {
            logger.error(t) { "${t.message}" }
            null
        }
    }

    /** The flow containing the configuration to use for Electrum. If null, we do not know what conf to use. */
    private val _electrumConfig by lazy { MutableStateFlow<ElectrumConfig?>(null) }
    fun electrumConfig(): StateFlow<ElectrumConfig?> = _electrumConfig

    /** Use this method to set a server to connect to. If null, will connect to a random server. */
    fun updateElectrumConfig(server: ServerAddress?) {
        _electrumConfig.value = server?.let { ElectrumConfig.Custom(it) } ?: ElectrumConfig.Random(randomElectrumServer())
    }

    private fun randomElectrumServer() = when (chain) {
        Chain.Mainnet -> electrumMainnetConfigurations.random()
        Chain.Testnet -> electrumTestnetConfigurations.random()
        Chain.Regtest -> platformElectrumRegtestConf()
    }.asServerAddress(tls = TcpSocket.TLS.SAFE)

    /** The flow containing the electrum header responses messages. */
    private val _electrumMessages by lazy { MutableStateFlow<HeaderSubscriptionResponse?>(null) }
    fun electrumMessages(): StateFlow<HeaderSubscriptionResponse?> = _electrumMessages

    private fun watchElectrumMessages() = launch {
        electrumClient.openNotificationsSubscription().consumeAsFlow().filterIsInstance<HeaderSubscriptionResponse>()
            .collect {
                _electrumMessages.value = it
            }
    }

    // Tor configuration
    private val isTorEnabled = MutableStateFlow(false)
    fun subscribeToIsTorEnabled(): StateFlow<Boolean> = isTorEnabled
    fun updateTorUsage(enabled: Boolean): Unit {
        isTorEnabled.value = enabled
    }
}
