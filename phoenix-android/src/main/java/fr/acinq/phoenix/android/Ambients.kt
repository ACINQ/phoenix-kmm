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
import androidx.compose.ui.platform.AmbientContext
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.navigate
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.KeyState
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.ctrl.ControllerFactory
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


typealias CF = ControllerFactory

val BusinessFactory = staticAmbientOf<PhoenixBusiness?>(null)
val AmbientControllerFactory = staticAmbientOf<ControllerFactory?>(null)
val AmbientNavController = staticAmbientOf<NavHostController?>(null)
val AmbientKeyState = staticAmbientOf<KeyState>(null)

@Composable
val navController: NavHostController
    get() = AmbientNavController.current ?: error("no navigation controller defined")

@Composable
val keyState: KeyState
    get() = AmbientKeyState.current

@Composable
val controllerFactory: ControllerFactory
    get() = AmbientControllerFactory.current ?: error("No controller factory set. Please use appView or mockView.")

@Composable
val business: PhoenixBusiness
    get() = BusinessFactory.current ?: error("business is not available")

@Composable
val application: PhoenixApplication
    get() = AmbientContext.current.applicationContext as? PhoenixApplication
        ?: error("Application is not of type PhoenixApplication. Are you using appView in preview?")

fun NavHostController.navigate(screen: Screen, arg: String? = null, builder: NavOptionsBuilder.() -> Unit = {}) {
    val route = if (arg.isNullOrBlank()) screen.route else "${screen.route}/$arg"
    newLogger<NavController>(LoggerFactory.default).debug { "navigating to $route" }
    try {
        navigate(route, builder)
    } catch (e: Exception) {
        newLogger(LoggerFactory.default).error(e) { "failed to navigate to $route" }
    }
}