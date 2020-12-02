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

import androidx.compose.foundation.layout.Column
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Providers
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.android.*

@ExperimentalMaterialApi
@Composable
fun AppView(wallet: WalletState) {
    val navController = rememberNavController()
    Providers(
        ControllerFactoryAmbient provides application.business.controllers,
        NavControllerAmbient provides navController,
        WalletStateAmbient provides wallet
    ) {
        Column {
            Text(currentRoute().toString())
            NavHost(navController = navController, startDestination = Screen.Startup.route) {
                composable(Screen.Startup.fullRoute) {
                    StartupView()
                }
                composable(Screen.InitWallet.fullRoute) {
                    InitWallet()
                }
                composable(Screen.CreateWallet.fullRoute) {
                    CreateWalletView()
                }
                composable(Screen.RestoreWallet.fullRoute) {
                    RestoreWalletView()
                }
                composable(Screen.Home.fullRoute) {
                    HomeView()
                }
                composable(Screen.Receive.fullRoute) {
                    ReceiveView()
                }
                composable(Screen.ReadData.fullRoute) {
                    ReadDataView()
                }
                composable(Screen.Send.fullRoute) { backStackEntry ->
                    SendView(backStackEntry.arguments?.getString("request")?.run {
                        PaymentRequest.read(cleanUpInvoice(this))
                    })
                }
                composable(Screen.Settings.fullRoute) {
                    SettingsView()
                }
                composable(Screen.DisplaySeed.fullRoute) {
                    SeedView()
                }
            }
        }
    }
}

private fun cleanUpInvoice(input: String): String {
    val trimmed = input.replace("\\u00A0", "").trim()
    return when {
        trimmed.startsWith("lightning://", true) -> trimmed.drop(12)
        trimmed.startsWith("lightning:", true) -> trimmed.drop(10)
        trimmed.startsWith("bitcoin://", true) -> trimmed.drop(10)
        trimmed.startsWith("bitcoin:", true) -> trimmed.drop(8)
        else -> trimmed
    }
}
