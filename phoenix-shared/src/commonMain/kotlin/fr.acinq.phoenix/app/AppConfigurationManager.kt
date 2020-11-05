package fr.acinq.phoenix.app

import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.blockchain.electrum.HeaderSubscriptionResponse
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.utils.TAG_APPLICATION
import fr.acinq.phoenix.utils.TAG_CHAIN
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.serialization.SerializationException
import org.kodein.db.*
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.direct
import org.kodein.di.instance
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class, ExperimentalStdlibApi::class)
class AppConfigurationManager(override val di: DI) : DIAware, CoroutineScope by MainScope() {
    private val db: DB by instance(tag = TAG_APPLICATION)
    private val electrumClient: ElectrumClient by instance()
    private val httpClient: HttpClient by instance()
    private val chain: Chain by instance(tag = TAG_CHAIN)

    private val logger = direct.instance<LoggerFactory>().newLogger(AppConfigurationManager::class)

    private val electrumServerUpdates = ConflatedBroadcastChannel<ElectrumServer>()
    fun openElectrumServerUpdateSubscription(): ReceiveChannel<ElectrumServer> =
        electrumServerUpdates.openSubscription()

    /*
        TODO Manage updates for connection configurations:
            e.g. for Electrum Server : reconnect to new server
     */
    init {
        db.on<ElectrumServer>().register {
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
        launchUpdateRates() // TODO do we need to manage cancellation?
    }

    // General
    private val appConfigurationKey = db.key<AppConfiguration>(0)
    private fun createAppConfiguration(): AppConfiguration {
        if (db[appConfigurationKey] == null) {
            logger.info { "Create app configuration" }
            db.put(AppConfiguration())
        }
        return db[appConfigurationKey] ?: error("App configuration must be initialized.")
    }

    fun getAppConfiguration(): AppConfiguration = db[appConfigurationKey] ?: createAppConfiguration()

    fun putFiatCurrency(fiatCurrency: FiatCurrency) {
        logger.info { "Change fiat currency [$fiatCurrency]" }
        db.put(appConfigurationKey, getAppConfiguration().copy(fiatCurrency = fiatCurrency))
    }

    fun putBitcoinUnit(bitcoinUnit: BitcoinUnit) {
        logger.info { "Change bitcoin unit [$bitcoinUnit]" }
        db.put(appConfigurationKey, getAppConfiguration().copy(bitcoinUnit = bitcoinUnit))
    }

    fun putAppTheme(appTheme: AppTheme) {
        logger.info { "Change app theme [$appTheme]" }
        db.put(appConfigurationKey, getAppConfiguration().copy(appTheme = appTheme))
    }

    // Electrum
    private val electrumServerKey = db.key<ElectrumServer>(0)
    private fun createElectrumConfiguration(): ElectrumServer {
        if (db[electrumServerKey] == null) {
            logger.info { "Create ElectrumX configuration" }
            setRandomElectrumServer()
        }
        return db[electrumServerKey] ?: error("ElectrumServer must be initialized.")
    }

    fun getElectrumServer(): ElectrumServer = db[electrumServerKey] ?: createElectrumConfiguration()

    private fun putElectrumServer(electrumServer: ElectrumServer) {
        logger.info { "Update electrum configuration [$electrumServer]" }
        db.put(electrumServerKey, electrumServer)
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

    // Bitcoin exchange rates
    public fun getBitcoinRates(): List<BitcoinPriceRate> = db.find<BitcoinPriceRate>().all().useModels {it.toList()}
    public fun getBitcoinRate(fiatCurrency: FiatCurrency): BitcoinPriceRate = db.find<BitcoinPriceRate>().byId(fiatCurrency.name).model()

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
                    logger.error { "An issue occurred while retrieving exchange rates from API." }
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

            db.execBatch {
                logger.verbose { "Saving price rates: $exchangeRates" }
                exchangeRates.forEach {
                    db.put(it)
                }
            }

            yield()
            delay(5.minutes)
        }
    }
}
