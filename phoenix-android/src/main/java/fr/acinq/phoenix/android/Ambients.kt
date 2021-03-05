/*
 * Copyright 2020 ACINQ SAS
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.navigate
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.security.KeyState
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.ctrl.ControllerFactory
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.CurrencyUnit
import fr.acinq.phoenix.data.FiatCurrency
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


typealias CF = ControllerFactory

val LocalBusiness = staticCompositionLocalOf<PhoenixBusiness?> { null }
val LocalControllerFactory = staticCompositionLocalOf<ControllerFactory?> { null }
val LocalNavController = staticCompositionLocalOf<NavHostController?> { null }
val LocalKeyState = compositionLocalOf<KeyState> { KeyState.Unknown }
val LocalBitcoinUnit = compositionLocalOf { BitcoinUnit.Sat }
val LocalFiatCurrency = compositionLocalOf { FiatCurrency.USD }
val LocalFiatRate = compositionLocalOf { -1.0 }
val LocalShowInFiat = compositionLocalOf { false }

val navController
    @Composable
    get() = LocalNavController.current ?: error("navigation controller is not available")

val keyState
    @Composable
    get() = LocalKeyState.current

val prefUnit: CurrencyUnit
    @Composable
    get() = if (prefShowInFiat) {
        prefFiatCurrency
    } else {
        prefBitcoinUnit
    }

val prefBitcoinUnit
    @Composable
    get() = LocalBitcoinUnit.current

val prefFiatCurrency
    @Composable
    get() = LocalFiatCurrency.current

val fiatRate
    @Composable
    get() = LocalFiatRate.current

val prefShowInFiat
    @Composable
    get() = LocalShowInFiat.current

val controllerFactory: ControllerFactory
    @Composable
    get() = LocalControllerFactory.current ?: error("No controller factory set. Please use appView or mockView.")

val business: PhoenixBusiness
    @Composable
    get() = LocalBusiness.current ?: error("business is not available")

val application: PhoenixApplication
    @Composable
    get() = LocalContext.current.applicationContext as? PhoenixApplication ?: error("Application is not of type PhoenixApplication. Are you using appView in preview?")

fun NavHostController.navigate(screen: Screen, arg: String? = null, builder: NavOptionsBuilder.() -> Unit = {}) {
    val route = if (arg.isNullOrBlank()) screen.route else "${screen.route}/$arg"
    newLogger<NavController>(LoggerFactory.default).debug { "navigating to $route" }
    try {
        navigate(route, builder)
    } catch (e: Exception) {
        newLogger(LoggerFactory.default).error(e) { "failed to navigate to $route" }
    }
}