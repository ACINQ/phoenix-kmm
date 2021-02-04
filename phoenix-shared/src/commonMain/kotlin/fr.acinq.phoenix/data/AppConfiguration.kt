package fr.acinq.phoenix.data

import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.io.TcpSocket
import fr.acinq.eclair.utils.ServerAddress
import kotlinx.serialization.Serializable
import org.kodein.db.model.orm.Metadata
import kotlin.math.roundToLong

enum class Chain { MAINNET, TESTNET, REGTEST }

@Serializable
enum class BitcoinUnit(
    val label: String,
    val explanation: String,
    val abbrev: String
) {
    Satoshi("Satoshi","0.00000001 BTC", "sat"),
    Bits("Bits", "0.000001 BTC", "bits"),
    MilliBitcoin("Milli-Bitcoin", "0.001 BTC", "mbtc"),
    Bitcoin("Bitcoin", "", "btc"),
    ;

    companion object default {
        val values = BitcoinUnit.values().toList()
    }
}

fun Double.toMilliSatoshi(unit: BitcoinUnit): MilliSatoshi =
    when (unit) {
        BitcoinUnit.Satoshi -> MilliSatoshi((this * 1_000.0).roundToLong())
        BitcoinUnit.Bits -> MilliSatoshi((this * 100_000.0).roundToLong())
        BitcoinUnit.MilliBitcoin -> MilliSatoshi((this * 100_000_000.0).roundToLong())
        BitcoinUnit.Bitcoin -> MilliSatoshi((this * 100_000_000_000.0).roundToLong())
    }

@Serializable
enum class FiatCurrency(
    val shortLabel: String,
    val longLabel: String
) {

    AUD("AUD", "Australian Dollar"),
    BRL("BRL", "Brazilian Real"),
    CAD("CAD", "Canadian Dollar"),
    CHF("CHF", "Swiss Franc"),
    CLP("CLP", "Chilean Peso"),
    CNY("CNY", "Chinese Yuan"),
    DKK("DKK", "Danish Krone"),
    EUR("EUR", "Euro"),
    GBP("GBP", "Great British Pound"),
    HKD("HKD", "Hong Kong Dollar"),
    INR("INR", "Indian Rupee"),
    ISK("ISK", "Icelandic Kròna"),
    JPY("JPY", "Japanese Yen"),
    KRW("KRW", "Korean Won"),
    MXN("MXN", "Mexican Peso"),
    NZD("NZD", "New Zealand Dollar"),
    PLN("PLN", "Polish Zloty"),
    RUB("RUB", "Russian Ruble"),
    SEK("SEK", "Swedish Krona"),
    SGD("SGD", "Singapore Dollar"),
    THB("THB", "Thai Baht"),
    TWD("TWD", "Taiwan New Dollar"),
    USD("USD", "United States Dollar");

    companion object default {
        val values = FiatCurrency.values().toList()
    }
}

@Serializable
data class ElectrumServer(
    // Unique ID a their is only one configuration per app
    override val id: Int = 0,
    // TODO if not customized, should be dynamic and random
    val host: String,
    val port: Int,
    val tls: TcpSocket.TLS? = TcpSocket.TLS.SAFE,
    val customized: Boolean = false,
    val blockHeight: Int = 0,
    val tipTimestamp: Long = 0
) : Metadata

fun ElectrumServer.address(): String = "$host:$port"
fun ElectrumServer.asServerAddress(): ServerAddress = ServerAddress(host, port, tls)
