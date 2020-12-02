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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.android.mvi.MVIView
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.ctrl.Scan
import fr.acinq.phoenix.data.BitcoinUnit
import navController
import navigate
import requireWallet

@Composable
fun SendView(request: PaymentRequest?) {
    requireWallet(from = Screen.Send) {
        val logger = logger()
        MVIView(CF::scan) { model, postIntent ->
            val nc = navController
            Column {
                Text("do you want to pay $request")
                Text("amount=${request?.amount?.truncateToSatoshi()}sat")
                var amount by remember { mutableStateOf((request?.amount ?: MilliSatoshi(0)).truncateToSatoshi().toLong().toDouble()) }
                TextField(
                    backgroundColor = Color.Transparent,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    value = amount.toString(),
                    onValueChange = {
                        amount = it.toDouble()
                    })
                Button({
                    if (request != null) {
                        postIntent(Scan.Intent.Send(request.write(), amount, BitcoinUnit.Satoshi))
                        nc.navigate(Screen.Home)
                    }
                }) {
                    Text("Send")
                }
            }
        }
    }
}