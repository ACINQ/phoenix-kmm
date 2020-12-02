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

import CF
import Screen
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ContextAmbient
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import copyToClipboard
import fr.acinq.phoenix.android.mvi.MVIView
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.ctrl.Receive
import fr.acinq.phoenix.data.BitcoinUnit
import navController
import requireWallet

@Composable
fun ReceiveView() {
    requireWallet(from = Screen.Receive) {
        val logger = logger()
        val nc = navController
        val context = ContextAmbient.current.applicationContext
        MVIView(CF::receive) { model, postIntent ->
            when (model) {
                is Receive.Model.Awaiting -> {
                    Button({ postIntent(Receive.Intent.Ask(amount = 10000.0, unit = BitcoinUnit.Satoshi, desc = "whatever")) }) {
                        Text("Generate invoice")
                    }
                }
                is Receive.Model.Generating -> {
                    Text("generating invoice...")
                }
                is Receive.Model.Generated -> {
                    Column {
                        Text("invoice generated!")
                        Text(text = model.request, style = TextStyle(), modifier = Modifier.padding(16.dp))
                        Button({
                            copyToClipboard(context, data = model.request)
                        }) {
                            Text("Copy invoice")
                        }
                    }
                }
            }
        }
    }
}