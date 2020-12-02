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
import androidx.compose.runtime.staticAmbientOf
import androidx.compose.ui.platform.ContextAmbient
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.navigate
import fr.acinq.eclair.utils.Either
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.ctrl.ControllerFactory
import fr.acinq.phoenix.data.Wallet
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


typealias WalletState = Either<UnknownWalletState, Wallet?>

object UnknownWalletState

fun WalletState.isReady() = this.isRight && this.right != null

typealias CF = ControllerFactory

val ControllerFactoryAmbient = staticAmbientOf<ControllerFactory?>(null)
val NavControllerAmbient = staticAmbientOf<NavHostController?>(null)
val WalletStateAmbient = staticAmbientOf<WalletState>(null)

@Composable
val navController: NavHostController
    get() = NavControllerAmbient.current ?: error("no navigation controller defined")

@Composable
val wallet: WalletState
    get() = WalletStateAmbient.current

@Composable
val controllerFactory: ControllerFactory
    get() = ControllerFactoryAmbient.current ?: error("No controller factory set. Please use appView or mockView.")

@Composable
val application: PhoenixApplication
    get() = ContextAmbient.current.applicationContext as? PhoenixApplication
        ?: error("Application is not of type PhoenixApplication. Are you using appView in preview?")

fun NavHostController.navigate(screen: Screen, arg: String? = null, builder: NavOptionsBuilder.() -> Unit = {}) {
    val route = if (arg.isNullOrBlank()) screen.route else "${screen.route}/$arg"
    newLogger<NavController>(LoggerFactory.default).verbose { "navigating to $route" }
    try {
        navigate(route, builder)
    } catch (e: Exception) {
        newLogger(LoggerFactory.default).error(e) { "failed to navigate to $route" }
    }
}