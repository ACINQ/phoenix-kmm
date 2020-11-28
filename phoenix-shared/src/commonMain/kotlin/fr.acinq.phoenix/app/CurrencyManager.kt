package fr.acinq.phoenix.app

import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.blockchain.electrum.HeaderSubscriptionResponse
import fr.acinq.phoenix.ctrl.EventBus
import fr.acinq.phoenix.ctrl.Event
import fr.acinq.phoenix.data.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import org.kodein.db.*
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

object FiatExchangeRatesUpdated : Event()

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class, ExperimentalStdlibApi::class)
class CurrencyManager(
    loggerFactory: LoggerFactory,
    private val appDB: DB,
    private val httpClient: HttpClient,
    private val eventBus: EventBus
) : CoroutineScope by MainScope() {

    private val logger = newLogger(loggerFactory)

	init {
        launchUpdateRates() // Do we need to manage cancellation ?
    }

	public fun getBitcoinRates(): List<BitcoinPriceRate> {
		return appDB.find<BitcoinPriceRate>().all().useModels { it.toList() }
	}
    public fun getBitcoinRate(fiatCurrency: FiatCurrency): BitcoinPriceRate {

		// Todo: What happens here if there aren't any entries in the database ?
		//       Does it actually crash ?!?!
		// 
		return appDB.find<BitcoinPriceRate>().byId(fiatCurrency.name).model()
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

            appDB.execBatch {
                logger.verbose { "Saving price rates: $exchangeRates" }
                exchangeRates.forEach {
                    appDB.put(it)
                }
            }

            eventBus.send(FiatExchangeRatesUpdated)
            yield()
            delay(5.minutes)
        }
    }
}