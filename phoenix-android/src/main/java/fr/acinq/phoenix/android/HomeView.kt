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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.AmbientContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.eclair.db.IncomingPayment
import fr.acinq.eclair.db.OutgoingPayment
import fr.acinq.eclair.db.WalletPayment
import fr.acinq.phoenix.android.mvi.MVIView
import fr.acinq.phoenix.android.utils.logger
import navController
import navigate
import requireWallet

@Composable
fun HomeView() {
    requireWallet(from = Screen.Home) {
        val logger = logger()
        MVIView(CF::home) { model, postIntent ->
            Column {
                AmountView(
                    amount = model.balanceSat,
                    amountSize = 48.sp,
                    //unit = Satoshi,
                    unitColor = MaterialTheme.colors.primary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(16.dp)
                )
//                Text("electrum=${model.connections.electrum}")
//                Text("peer=${model.connections.peer}")
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(model.payments) {
                        PaymentLine(payment = it)
                    }
                }
                BottomBar()
            }
        }
    }
}

@Composable
private fun PaymentLine(payment: WalletPayment) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(12.dp)
    ) {
        when (payment) {
            is OutgoingPayment -> when (payment.status) {
                is OutgoingPayment.Status.Failed -> Image(
                    imageVector = vectorResource(R.drawable.ic_payment_failed),
                    contentDescription = stringResource(id = R.string.paymentdetails_status_sent_failed),
                    modifier = Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colors.onPrimary))
                is OutgoingPayment.Status.Pending -> Image(
                    imageVector = vectorResource(R.drawable.ic_payment_pending),
                    contentDescription = stringResource(id = R.string.paymentdetails_status_sent_pending),
                    modifier = Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colors.onPrimary))
                is OutgoingPayment.Status.Succeeded -> Box(modifier = Modifier
                    .clip(CircleShape)
                    .background(color = MaterialTheme.colors.primary)
                    .padding(4.dp)) {
                    Image(
                        imageVector = vectorResource(R.drawable.ic_payment_success),
                        contentDescription = stringResource(id = R.string.paymentdetails_status_sent_successful),
                        modifier = Modifier.size(18.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colors.onPrimary))
                }
            }
            is IncomingPayment -> when (payment.received) {
                null -> Image(
                    imageVector = vectorResource(R.drawable.ic_payment_pending),
                    contentDescription = stringResource(id = R.string.paymentdetails_status_received_pending),
                    modifier = Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colors.onPrimary))
                else -> Box(modifier = Modifier
                    .clip(CircleShape)
                    .background(color = MaterialTheme.colors.primary)
                    .padding(4.dp)) {
                    Image(
                        imageVector = vectorResource(R.drawable.ic_payment_success),
                        contentDescription = stringResource(id = R.string.paymentdetails_status_received_successful),
                        modifier = Modifier.size(18.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colors.onPrimary))
                }
            }
        }
//        Text(text = payment.desc.takeIf { it.isNotBlank() } ?: stringResource(id = R.string.paymentholder_no_desc), modifier = Modifier.weight(1f).padding(8.dp))
//        AmountView(amount = payment.amountSat, amountColor = if (payment.amountSat < 0) errorColor() else successColor(), unit = BitcoinUnit.Satoshi)
    }
}

@Composable
private fun AmountView(
    amount: Long,
    modifier: Modifier = Modifier,
    amountSize: TextUnit = AmbientTextStyle.current.fontSize,
    amountColor: Color = AmbientContentColor.current,
    //unit: BitcoinUnit,
    unitSize: TextUnit = AmbientTextStyle.current.fontSize,
    unitColor: Color = AmbientContentColor.current,
) {
    Row(horizontalArrangement = Arrangement.Center, modifier = modifier) {
        Text(
            amount.toString(), style = TextStyle(fontSize = amountSize, color = amountColor),
            modifier = Modifier.alignBy(FirstBaseline)
        )
        Text(
            "FIXME", style = TextStyle(fontSize = unitSize, color = unitColor),
            modifier = Modifier
                .padding(start = 8.dp)
                .alignBy(FirstBaseline)
        )
    }
}

@Composable
private fun BottomBar() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topLeft = 16.dp, topRight = 16.dp)),
    ) {
        val nc = navController
        val height = 80.dp
        Button(
            onClick = { nc.navigate(Screen.Settings) },
            elevation = null,
            shape = RectangleShape,
            contentPadding = PaddingValues(24.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface),
            modifier = Modifier.height(height)
        ) {
            PhoenixIcon(R.drawable.ic_settings, Modifier.size(ButtonDefaults.IconSize))
        }
        Button(
            onClick = { nc.navigate(Screen.Receive) },
            elevation = null,
            shape = RectangleShape,
            contentPadding = PaddingValues(24.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface),
            modifier = Modifier
                .height(height)
                .weight(1f)
        ) {
            IconWithText(R.drawable.ic_receive, "Receive")
        }
        val context = AmbientContext.current.applicationContext
        Button(
            onClick = { nc.navigate(Screen.ReadData) },
            elevation = null,
            shape = RectangleShape,
            contentPadding = PaddingValues(24.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface),
            modifier = Modifier
                .height(height)
                .weight(1f)
        ) {
            IconWithText(R.drawable.ic_send, "Send")
        }
    }
}

@Composable
fun IconWithText(icon: Int, text: String, iconTint: Color = AmbientContentColor.current) {
    PhoenixIcon(icon, Modifier.size(ButtonDefaults.IconSize), iconTint)
    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
    Text(text)
}

@Composable
fun PhoenixIcon(
    resourceId: Int,
    modifier: Modifier = Modifier,
    tint: Color = AmbientContentColor.current
) {
    Image(imageVector = vectorResource(id = resourceId),
        contentDescription = "",
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint)
    )
}