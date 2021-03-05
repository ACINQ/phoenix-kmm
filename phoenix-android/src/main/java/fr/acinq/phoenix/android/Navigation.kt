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

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.KEY_ROUTE
import androidx.navigation.compose.currentBackStackEntryAsState
import fr.acinq.phoenix.android.security.KeyState
import fr.acinq.phoenix.android.utils.logger


sealed class Screen(val route: String, val arg: String? = null) {
    val fullRoute by lazy { if (arg.isNullOrBlank()) route else "${route}/{$arg}" }

    object InitWallet : Screen("initwallet")
    object CreateWallet : Screen("createwallet")
    object RestoreWallet : Screen("restorewallet")
    object Startup : Screen("startup")
    object Home : Screen("home")
    object Settings : Screen("settings")
    object DisplaySeed : Screen("settings/seed")
    object Receive : Screen("receive")
    object ReadData : Screen("readdata")
    object Send : Screen("send", "request")
}

@Composable
fun requireWallet(
    from: Screen,
    children: @Composable () -> Unit
) {
    if (keyState !is KeyState.Present) {
        logger().warning { "accessing screen=$from with keyState=$keyState" }
        navController.navigate(Screen.Startup)
        Text("redirecting...")
    } else {
        logger().debug { "access to screen=$from granted" }
        children()
    }
}

@Composable
fun currentRoute(): String? {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    return navBackStackEntry?.arguments?.getString(KEY_ROUTE)
}

