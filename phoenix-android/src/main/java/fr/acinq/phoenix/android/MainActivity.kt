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

package fr.acinq.phoenix.android

import AppView
import UnknownWalletState
import WalletState
import android.Manifest
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.setContent
import androidx.core.app.ActivityCompat
import androidx.ui.tooling.preview.Devices
import androidx.ui.tooling.preview.Preview
import fr.acinq.eclair.utils.Either
import fr.acinq.phoenix.android.mvi.MockView
import fr.acinq.phoenix.ctrl.Initialization
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch


@ExperimentalCoroutinesApi
class MainActivity : AppCompatActivity() {

    @ExperimentalMaterialApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.CAMERA), 1234)
        var wallet by mutableStateOf<WalletState>(Either.Left(UnknownWalletState))
        MainScope().launch {
            (application as PhoenixApplication).business.walletManager.openWalletUpdatesSubscription().consumeEach {
                (application as PhoenixApplication).business.start()
                wallet = Either.Right(it)
                return@consumeEach
            }
        }
        setContent {
            PhoenixAndroidTheme {
                AppView(wallet)
            }
        }
    }
}

val MockModelInitialization = Initialization.Model.Initialization

@Preview(device = Devices.PIXEL_3)
@Composable
fun DefaultPreview() {
    MockView { PhoenixAndroidTheme { InitWallet() } }
}
