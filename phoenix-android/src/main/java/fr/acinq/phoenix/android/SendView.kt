package fr.acinq.phoenix.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.android.mvi.*
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.ctrl.Scan
import fr.acinq.phoenix.data.BitcoinUnit
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import fr.acinq.bitcoin.Satoshi
import fr.acinq.eclair.MilliSatoshi

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