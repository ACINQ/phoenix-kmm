package fr.acinq.phoenix.android.settings

import CF
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import business
import fr.acinq.eclair.utils.Connection
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.mvi.MVIView
import fr.acinq.phoenix.android.utils.Prefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.ctrl.config.ElectrumConfiguration
import fr.acinq.phoenix.data.ElectrumConfig
import navController

@Composable
fun ElectrumView() {
    val log = logger()
    val nc = navController
    val context = LocalContext.current

    ScreenHeader(
        onBackClick = { nc.popBackStack() },
        title = stringResource(id = R.string.electrum_title),
        subtitle = stringResource(id = R.string.electrum_subtitle)
    )
    Prefs.saveElectrumServer(context, "")
    ScreenBody(padding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
        MVIView(CF::electrumConfiguration) { model, postIntent ->
            val showServerDialog = remember { mutableStateOf(false) }
            if (showServerDialog.value) {
                ElectrumServerDialog(
                    onConfirm = {
                        postIntent(ElectrumConfiguration.Intent.UpdateElectrumServer(it))
                        Prefs.saveElectrumServer(context, it)
                        showServerDialog.value = false
                    },
                    onCancel = { showServerDialog.value = false })
            }
            val connection = model.connection
            val config = model.configuration
            val title = when {
                connection == Connection.CLOSED && config is ElectrumConfig.Random -> stringResource(id = R.string.electrum_not_connected)
                connection == Connection.CLOSED && config is ElectrumConfig.Custom -> stringResource(id = R.string.electrum_not_connected_to_custom, config.server.host)
                connection == Connection.ESTABLISHING && config is ElectrumConfig.Random -> stringResource(id = R.string.electrum_connecting)
                connection == Connection.ESTABLISHING && config is ElectrumConfig.Custom -> stringResource(id = R.string.electrum_connecting_to_custom, config.server.host)
                connection == Connection.ESTABLISHED && config != null -> stringResource(id = R.string.electrum_connected, config.server.host)
                else -> stringResource(id = R.string.electrum_not_connected)
            }
            val description = when (config) {
                is ElectrumConfig.Random -> stringResource(id = R.string.electrum_server_desc_random)
                is ElectrumConfig.Custom -> stringResource(id = R.string.electrum_server_desc_custom)
                else -> null
            }
            Setting(title = title, description = description, onClick = { showServerDialog.value = true })
            if (model.blockHeight > 0) {
                Setting(title = stringResource(id = R.string.electrum_block_height_label), description = model.blockHeight.toString())
            }
            if (model.feeRate > 0) {
                Setting(title = stringResource(id = R.string.electrum_fee_rate_label), description = stringResource(id = R.string.electrum_fee_rate, model.feeRate.toString()))
            }
        }
        val xpub = business.getXpub() ?: "" to ""
        Setting(title = stringResource(id = R.string.electrum_xpub_label), description = stringResource(id = R.string.electrum_xpub_value, xpub.first, xpub.second))
    }
}

@Composable
private fun ElectrumServerDialog(onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    var useCustomServer by remember { mutableStateOf(true) }
    var address by remember { mutableStateOf("") }
    Dialog(onDismiss = onCancel, buttons = {
        Button(onClick = { onCancel() }, text = stringResource(id = R.string.btn_cancel), padding = PaddingValues(8.dp))
        Button(onClick = { onConfirm(address) }, text = stringResource(id = R.string.btn_ok), padding = PaddingValues(8.dp))
    }) {
        Column(Modifier.padding(16.dp)) {
            Row {
                Checkbox(checked = useCustomServer, onCheckedChange = { useCustomServer = it })
                Text(text = stringResource(id = R.string.electrum_dialog_checkbox))
            }
            InputText(text = address, onTextChange = { address = it })
        }
    }
}

@Composable
fun Setting(modifier: Modifier = Modifier, title: String, description: String?, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 50.dp, top = 10.dp, bottom = 10.dp, end = 16.dp)
            .then(modifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else Modifier
            )
    ) {
        Text(title, style = MaterialTheme.typography.subtitle2)
        Text(description ?: "", style = MaterialTheme.typography.caption)
    }
}