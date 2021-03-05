/*
 * Copyright 2019 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.android.utils

import android.content.Context
import android.os.Build
import android.text.Html
import android.text.Spanned
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.CurrencyUnit
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.data.toUnit
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToLong

object Converter {

    private val log = LoggerFactory.getLogger(Converter::class.java)

    private val DECIMAL_SEPARATOR = DecimalFormat().decimalFormatSymbols.decimalSeparator.toString()
    private var COIN_FORMAT: NumberFormat = NumberFormat.getInstance()
    private var FIAT_FORMAT: NumberFormat = NumberFormat.getInstance()

    init {
        FIAT_FORMAT.minimumFractionDigits = 2
        FIAT_FORMAT.maximumFractionDigits = 2
        FIAT_FORMAT.roundingMode = RoundingMode.CEILING // prevent converting very small bitcoin amounts to 0 in fiat
    }

    fun refreshCoinPattern(context: Context) {
        COIN_FORMAT = getCoinFormat(Prefs.getBitcoinUnit(context))
        COIN_FORMAT.roundingMode = RoundingMode.DOWN // better to underestimate
    }

    fun getCoinFormat(unit: BitcoinUnit) = when (unit) {
        BitcoinUnit.Sat -> DecimalFormat("###,###,###,##0")
        BitcoinUnit.Bit -> DecimalFormat("###,###,###,##0.##")
        BitcoinUnit.MBtc -> DecimalFormat("###,###,###,##0.#####")
        else -> DecimalFormat("###,###,###,##0.###########")
    }

    fun Double?.toPrettyString(unit: CurrencyUnit, withUnit: Boolean = true): String = (when (unit) {
        is BitcoinUnit -> this?.run { COIN_FORMAT.format(this) }
        is FiatCurrency -> this?.run { FIAT_FORMAT.format(this) }
        else -> "?!"
    } ?: "").run {
        if (withUnit) "$this $unit" else this
    }

    /** Returns a string representation of this Double. No scientific notation is used. Reuse [BigDecimal.toPlainString]. */
    fun Double?.toPlainString(): String = this?.takeIf { it > 0 }?.run { BigDecimal.valueOf(this).toPlainString() } ?: ""

    /** Converts [MilliSatoshi] to a fiat amount. */
    fun MilliSatoshi.toFiat(rate: Double): Double = this.msat.toDouble() * rate

    fun MilliSatoshi.toPrettyString(unit: CurrencyUnit, fiatRate: Double = -1.0, withUnit: Boolean = false): String = when {
        unit is BitcoinUnit -> this.toUnit(unit).toPrettyString(unit, withUnit)
        unit is FiatCurrency && fiatRate > 0 -> this.toFiat(fiatRate).toPrettyString(unit, withUnit)
        else -> "?!"
    }

    /** Converts this millis timestamp into a relative string date. */
    @Composable
    fun Long.toRelativeDateString(): String {
        val now = System.currentTimeMillis()
        val delay: Long = this - now
        return if (abs(delay) < 60 * 1000L) { // less than 1 minute ago
            stringResource(id = R.string.utils_date_just_now)
        } else {
            DateUtils.getRelativeTimeSpanString(this, now, delay).toString()
        }
    }

    /** Converts this millis timestamp into an absolute string date using the locale format. */
    fun Long.toAbsoluteDateString(): String = DateFormat.getDateTimeInstance().format(Date(this))

    @Composable
    public fun html(id: Int): Spanned {
        return html(stringResource(id = id))
    }

    public fun html(source: String): Spanned {
        return Html.fromHtml(source, Html.FROM_HTML_MODE_COMPACT
                and Html.FROM_HTML_SEPARATOR_LINE_BREAK_HEADING
                and Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH
                and Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST
                and Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM)
    }
}
