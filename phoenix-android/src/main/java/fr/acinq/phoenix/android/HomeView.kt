package fr.acinq.phoenix.android

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumnFor
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageAsset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.platform.ContextAmbient
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.mvi.*
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.Transaction

@Composable
fun HomeView() {
    requireWallet(from = Screen.Home) {
        val logger = logger()
        MVIView(CF::home) { model, postIntent ->
            Column {
                AmountView(
                    amount = model.balanceSat,
                    amountSize = 48.sp,
                    unit = BitcoinUnit.Satoshi,
                    unitColor = MaterialTheme.colors.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
                )
                Text("electrum=${model.connections.electrum}")
                Text("peer=${model.connections.peer}")
                LazyColumnFor(items = model.history, modifier = Modifier.weight(1f)) { tx ->
                    PaymentLine(payment = tx)
                }
                BottomBar()
            }
        }
    }
}

@Composable
private fun PaymentLine(payment: Transaction) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(12.dp)
    ) {
        when (payment.status) {
            Transaction.Status.Pending -> Icon(asset = vectorResource(R.drawable.ic_payment_pending))
            Transaction.Status.Failure -> Icon(asset = vectorResource(R.drawable.ic_payment_failed))
            Transaction.Status.Success -> Box(modifier = Modifier.clip(CircleShape).background(color = MaterialTheme.colors.primary).padding(4.dp)) {
                Icon(asset = vectorResource(R.drawable.ic_payment_success), tint = MaterialTheme.colors.onPrimary, modifier = Modifier.size(18.dp))
            }
        }
        Text(text = payment.desc.takeIf { it.isNotBlank() } ?: stringResource(id = R.string.paymentholder_no_desc), modifier = Modifier.weight(1f).padding(8.dp))
        AmountView(amount = payment.amountSat, amountColor = if (payment.amountSat < 0) errorColor() else successColor(), unit = BitcoinUnit.Satoshi)
    }
}

@Composable
private fun AmountView(
    amount: Long,
    amountSize: TextUnit = AmbientTextStyle.current.fontSize,
    amountColor: Color = AmbientContentColor.current,
    unit: BitcoinUnit,
    unitSize: TextUnit = AmbientTextStyle.current.fontSize,
    unitColor: Color = AmbientContentColor.current,
    modifier: Modifier = Modifier
) {
    Row(horizontalArrangement = Arrangement.Center, modifier = modifier) {
        Text(
            amount.toString(), style = TextStyle(fontSize = amountSize, color = amountColor),
            modifier = Modifier.alignBy(FirstBaseline)
        )
        Text(
            unit.abbrev, style = TextStyle(fontSize = unitSize, color = unitColor),
            modifier = Modifier.padding(start = 8.dp).alignBy(FirstBaseline)
        )
    }
}

@Composable
private fun BottomBar() {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topLeft = 16.dp, topRight = 16.dp)),
    ) {
        val nc = navController
        val height = 80.dp
        Button(
            onClick = { nc.navigate(Screen.Settings) },
            elevation = null,
            shape = RectangleShape,
            contentPadding = PaddingValues(24.dp),
            colors = ButtonConstants.defaultButtonColors(backgroundColor = MaterialTheme.colors.surface),
            modifier = Modifier.height(height)
        ) {
            PhoenixIcon(R.drawable.ic_settings, Modifier.size(ButtonConstants.DefaultIconSize))
        }
        Button(
            onClick = { nc.navigate(Screen.Receive) },
            elevation = null,
            shape = RectangleShape,
            contentPadding = PaddingValues(24.dp),
            colors = ButtonConstants.defaultButtonColors(backgroundColor = MaterialTheme.colors.surface),
            modifier = Modifier.height(height).weight(1f)
        ) {
            IconWithText(R.drawable.ic_receive, "Receive")
        }
        val context = ContextAmbient.current.applicationContext
        Button(
            onClick = { nc.navigate(Screen.ReadData) },
            elevation = null,
            shape = RectangleShape,
            contentPadding = PaddingValues(24.dp),
            colors = ButtonConstants.defaultButtonColors(backgroundColor = MaterialTheme.colors.surface),
            modifier = Modifier.height(height).weight(1f)
        ) {
            IconWithText(R.drawable.ic_send, "Send")
        }
    }
}

@Composable
fun IconWithText(icon: Int, text: String, iconTint: Color = AmbientContentColor.current) {
    PhoenixIcon(icon, Modifier.size(ButtonConstants.DefaultIconSize), iconTint)
    Spacer(Modifier.size(ButtonConstants.DefaultIconSpacing))
    Text(text)
}

@Composable
fun PhoenixIcon(
    resourceId: Int,
    modifier: Modifier = Modifier,
    tint: Color = AmbientContentColor.current
) {
    Icon(
        asset = vectorResource(id = resourceId),
        modifier = modifier,
        tint = tint
    )
}