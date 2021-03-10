package fr.acinq.phoenix.data

import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.io.TcpSocket
import fr.acinq.eclair.utils.ServerAddress
import kotlinx.serialization.Serializable
import org.kodein.db.model.orm.Metadata
import kotlin.math.roundToLong

enum class Chain { MAINNET, TESTNET, REGTEST }

interface CurrencyUnit

@Serializable
enum class BitcoinUnit : CurrencyUnit {
    Sat, Bit, MBtc, Btc;

    override fun toString(): String {
        return super.toString().toLowerCase()
    }
}

/** Converts a [Double] amount to [MilliSatoshi], assuming that this amount is in fiat. */
fun Double.toMilliSatoshi(fiatRate: Double): MilliSatoshi = (this / fiatRate).toMilliSatoshi(BitcoinUnit.Btc)

/** Converts a [Double] amount to [MilliSatoshi], assuming that this amount is in Bitcoin. */
fun Double.toMilliSatoshi(unit: BitcoinUnit): MilliSatoshi = when (unit) {
    BitcoinUnit.Sat -> MilliSatoshi((this * 1_000.0).roundToLong())
    BitcoinUnit.Bit -> MilliSatoshi((this * 100_000.0).roundToLong())
    BitcoinUnit.MBtc -> MilliSatoshi((this * 100_000_000.0).roundToLong())
    BitcoinUnit.Btc -> MilliSatoshi((this * 100_000_000_000.0).roundToLong())
}

/** Converts [MilliSatoshi] to another Bitcoin unit. */
fun MilliSatoshi.toUnit(unit: BitcoinUnit): Double = when (unit) {
    BitcoinUnit.Sat -> this.msat / 1_000.0
    BitcoinUnit.Bit -> this.msat / 100_000.0
    BitcoinUnit.MBtc -> this.msat / 100_000_000.0
    BitcoinUnit.Btc -> this.msat / 100_000_000_000.0
}

@Serializable
enum class FiatCurrency : CurrencyUnit {
    AUD, BRL, CAD, CHF, CLP, CNY, DKK, EUR, GBP, HKD, INR, ISK, JPY, KRW, MXN, NZD, PLN, RUB, SEK, SGD, THB, TWD, USD;

    companion object {
        fun valueOfOrNull(code: String): FiatCurrency? = try {
            valueOf(code)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

@Serializable
data class ElectrumServer(
    override val id: Int = 0,
    // TODO if not customized, should be dynamic and random
    val host: String,
    val port: Int,
    val customized: Boolean = false,
    val blockHeight: Int = 0,
    val tipTimestamp: Long = 0
) : Metadata

fun ElectrumServer.address(): String = "$host:$port"
fun ElectrumServer.asServerAddress(tls: TcpSocket.TLS? = null): ServerAddress = ServerAddress(host, port, tls)
