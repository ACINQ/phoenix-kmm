package fr.acinq.phoenix.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ContextAmbient
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.mvi.*
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.ctrl.Receive
import fr.acinq.phoenix.data.BitcoinUnit

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