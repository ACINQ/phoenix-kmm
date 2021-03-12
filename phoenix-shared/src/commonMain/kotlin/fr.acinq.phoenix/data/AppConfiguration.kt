package fr.acinq.phoenix.data

import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.utils.ServerAddress
import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

enum class Chain { MAINNET, TESTNET, REGTEST }

interface CurrencyUnit

@Serializable
enum class BitcoinUnit : CurrencyUnit {
    Sat, Bit, MBtc, Btc;

    override fun toString(): String {
        return super.toString().toLowerCase()
    }

    companion object default {
        val values = BitcoinUnit.values().toList()
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

    companion object default {
        val values = FiatCurrency.values().toList()
        fun valueOfOrNull(code: String): FiatCurrency? = try {
            valueOf(code)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

sealed class ElectrumConfig {
    abstract val server: ServerAddress
    data class Random(override val server: ServerAddress) : ElectrumConfig()
    data class Custom(override val server: ServerAddress) : ElectrumConfig()
}
