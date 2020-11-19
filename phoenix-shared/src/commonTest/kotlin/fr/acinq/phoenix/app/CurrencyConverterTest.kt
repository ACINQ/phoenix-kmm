package fr.acinq.phoenix.app

import fr.acinq.phoenix.data.BitcoinPriceRate
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency
import org.kodein.db.DB
import org.kodein.db.inmemory.inMemory
import org.kodein.db.orm.kotlinx.KotlinxSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencyConverterTest {
    private val db = DB.inMemory.open("application", KotlinxSerializer())
    private val currencyConverter = CurrencyConverter(db)

    init {
        db.put(BitcoinPriceRate(FiatCurrency.USD, price = 17501.35))
        db.put(BitcoinPriceRate(FiatCurrency.EUR, price = 14804.1))
    }

    @Test
    fun convertFromMillisatoshisToBTC() {
        assertEquals(1.0, currencyConverter.convert(100_000_000_000, BitcoinUnit.Bitcoin))
        assertEquals(1.0, currencyConverter.convert(100_000_000, BitcoinUnit.MilliBitcoin))
        assertEquals(1.0, currencyConverter.convert(100_000, BitcoinUnit.Bits))
        assertEquals(1.0, currencyConverter.convert(1_000, BitcoinUnit.Satoshi))

        val msat = 1_671_444_999L
        assertEquals(0.01671444, currencyConverter.convert(msat, BitcoinUnit.Bitcoin))
        assertEquals(16.71444, currencyConverter.convert(msat, BitcoinUnit.MilliBitcoin))
        assertEquals(16714.44, currencyConverter.convert(msat, BitcoinUnit.Bits))
        assertEquals(1671444.0, currencyConverter.convert(msat, BitcoinUnit.Satoshi))
    }

    @Test
    fun convertFromMillisatoshisToFiat() {
        assertEquals(17501.35, currencyConverter.convert(100_000_000_000, FiatCurrency.USD))
        assertEquals(17.50, currencyConverter.convert(100_000_000, FiatCurrency.USD))
        assertEquals(0.02, currencyConverter.convert(100_000, FiatCurrency.USD))
        assertEquals(0.0, currencyConverter.convert(1_000, FiatCurrency.USD))

        val msat = 1_671_444_999L
        assertEquals(292.53, currencyConverter.convert(msat, FiatCurrency.USD))
        assertEquals(247.44, currencyConverter.convert(msat, FiatCurrency.EUR))
    }
}
