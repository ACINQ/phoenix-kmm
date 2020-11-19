package fr.acinq.phoenix.app

import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.blockchain.electrum.HeaderSubscriptionResponse
import fr.acinq.eclair.utils.msat
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
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class, ExperimentalStdlibApi::class)
class CurrencyConverter(
    private val appDB: DB
) : CoroutineScope by MainScope() {

    // Bitcoin exchange rates
    private fun getBitcoinRates(): List<BitcoinPriceRate> = appDB.find<BitcoinPriceRate>().all().useModels {it.toList()}
    private fun getBitcoinRate(fiatCurrency: FiatCurrency): BitcoinPriceRate = appDB.find<BitcoinPriceRate>().byId(fiatCurrency.name).model()

    public fun convert(amountMsat: Long, to: CurrencyUnit) : Double {
        val amountSat = amountMsat.msat.truncateToSatoshi().sat

        return when(to) {
            is BitcoinUnit -> {
                when(to) {
                    BitcoinUnit.Satoshi -> amountSat.toDouble()
                    BitcoinUnit.Bits -> amountSat / 100.0
                    BitcoinUnit.MilliBitcoin -> amountSat / 100_000.0
                    BitcoinUnit.Bitcoin -> amountSat / 100_000_000.0
                }
            }
            is FiatCurrency -> {
                val btc = amountMsat / 100_000_000_000.0
                val rate = getBitcoinRate(to).price

                (btc * rate).roundTo(2)
            }
            else -> error("Currency Unit [$to] is not handle to convert from $amountMsat msat")
        }
    }

    private fun Double.roundTo(numFractionDigits: Int): Double {
        val factor = 10.0.pow(numFractionDigits.toDouble())
        return (this * factor).roundToInt() / factor
    }
}
