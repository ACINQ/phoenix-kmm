package fr.acinq.phoenix.app

import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.blockchain.electrum.HeaderSubscriptionResponse
import fr.acinq.phoenix.data.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.kodein.db.*
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class, ExperimentalStdlibApi::class)
class AppConfigurationManager(
    private val appDB: DB,
    private val electrumClient: ElectrumClient,
    private val httpClient: HttpClient,
    private val chain: Chain,
    loggerFactory: LoggerFactory
) : CoroutineScope by MainScope() {

    private val logger = newLogger(loggerFactory)

    private lateinit var electrumServerState: MutableStateFlow<ElectrumServer>
    fun electrumServer(): StateFlow<ElectrumServer> = electrumServerState

    private lateinit var displayedCurrencyState: MutableStateFlow<CurrencyUnit>
    fun displayedCurrency(): StateFlow<CurrencyUnit> = displayedCurrencyState

    /*
        TODO Manage updates for connection configurations:
            e.g. for Electrum Server : reconnect to new server
     */
    init {
        appDB.on<ElectrumServer>().register {
            didPut {
                if (it != getElectrumServer()) {
                    electrumServerState.value = it
                }
            }
        }
        appDB.on<AppConfiguration>().register {
            didPut {
                val newDisplayedCurrency = when(it.displayedCurrency) {
                    DisplayedCurrency.FIAT -> it.fiatCurrency
                    DisplayedCurrency.BITCOIN -> it.bitcoinUnit
                }
                if (newDisplayedCurrency != displayedCurrencyState.value) {
                    displayedCurrencyState.value = newDisplayedCurrency
                }
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
        launchUpdateRates() // TODO do we need to manage cancellation?
        initConfiguration()
    }

    // General
    private fun initConfiguration() = launch {
        logger.info { "loading existing app configuration" }

        // Get or Create the app configuration
        val appConfiguration = getAppConfiguration()

        // Load state for displayed currency
        val currency = when(appConfiguration.displayedCurrency) {
            DisplayedCurrency.FIAT -> appConfiguration.fiatCurrency
            DisplayedCurrency.BITCOIN -> appConfiguration.bitcoinUnit
        }
        displayedCurrencyState = MutableStateFlow(currency)

        // Get or Create the electrum configuration
        electrumServerState = MutableStateFlow(getElectrumServer())
    }

    private val appConfigurationKey = appDB.key<AppConfiguration>(0)
    private fun createAppConfiguration(): AppConfiguration {
        if (appDB[appConfigurationKey] == null) {
            logger.info { "create app configuration" }
            appDB.put(AppConfiguration())
        }
        return appDB[appConfigurationKey] ?: error("app configuration must be initialized")
    }

    fun getAppConfiguration(): AppConfiguration = appDB[appConfigurationKey] ?: createAppConfiguration()

    fun putFiatCurrency(fiatCurrency: FiatCurrency) {
        logger.info { "change fiat currency=$fiatCurrency" }
        appDB.put(appConfigurationKey, getAppConfiguration().copy(fiatCurrency = fiatCurrency))
    }

    fun putBitcoinUnit(bitcoinUnit: BitcoinUnit) {
        logger.info { "change bitcoin unit=$bitcoinUnit" }
        appDB.put(appConfigurationKey, getAppConfiguration().copy(bitcoinUnit = bitcoinUnit))
    }

    fun switchDisplayedCurrency() {
        val displayedCurrency = when(getAppConfiguration().displayedCurrency) {
            DisplayedCurrency.FIAT -> DisplayedCurrency.BITCOIN
            DisplayedCurrency.BITCOIN -> DisplayedCurrency.FIAT
        }

        logger.info { "change displayed currency unit=$displayedCurrency" }
        appDB.put(appConfigurationKey, getAppConfiguration().copy(displayedCurrency = displayedCurrency))
    }

    fun putAppTheme(appTheme: AppTheme) {
        logger.info { "change app theme=$appTheme" }
        appDB.put(appConfigurationKey, getAppConfiguration().copy(appTheme = appTheme))
    }

    // Electrum
    private val electrumServerKey = appDB.key<ElectrumServer>(0)
    private fun createElectrumConfiguration(): ElectrumServer {
        if (appDB[electrumServerKey] == null) {
            logger.info { "create ElectrumX configuration" }
            setRandomElectrumServer()
        }
        return appDB[electrumServerKey] ?: error("ElectrumServer must be initialized.")
    }

    fun getElectrumServer(): ElectrumServer = appDB[electrumServerKey] ?: createElectrumConfiguration()

    private fun putElectrumServer(electrumServer: ElectrumServer) {
        logger.info { "update electrum configuration=$electrumServer" }
        appDB.put(electrumServerKey, electrumServer)
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

    private fun launchUpdateRates() = launch {
        while (isActive) {
            val priceRates = buildMap<String, Double> {
                try {
                    val rates = httpClient.get<Map<String, PriceRate>>("https://blockchain.info/ticker")
                    rates.forEach {
                        put(it.key, it.value.last)
                    }

                    val response = httpClient.get<MxnApiResponse>("https://api.bitso.com/v3/ticker/?book=btc_mxn")
                    if (response.success) put(FiatCurrency.MXN.name, response.payload.last)
                } catch (t: Throwable) {
                    logger.error { "an issue occurred while retrieving exchange rates from API." }
                    logger.error(t)
                }
            }

            val exchangeRates =
                FiatCurrency.values()
                    .filter { it.name in priceRates.keys }
                    .mapNotNull { fiatCurrency ->
                        priceRates[fiatCurrency.name]?.let { priceRate ->
                            BitcoinPriceRate(fiatCurrency, priceRate)
                        }
                    }

            appDB.execBatch {
                logger.debug { "saving price rates=$exchangeRates" }
                exchangeRates.forEach {
                    appDB.put(it)
                }
            }

            yield()
            delay(5.minutes)
        }
    }
}
