package fr.acinq.phoenix.android

import android.os.Build
import android.text.Html
import android.text.Spanned
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.mvi.*
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.ctrl.config.RecoveryPhraseConfiguration
import fr.acinq.phoenix.data.Wallet


@Composable
fun SettingsView() {
    val nc = navController
    Column(modifier = Modifier.fillMaxWidth()) {
        Button(onClick = { nc.navigate(Screen.DisplaySeed) }) {
            IconWithText(R.drawable.ic_key, stringResource(R.string.settings_display_seed))
        }
    }
}

@Composable
fun SeedView() {
    val logger = logger()
    if (!wallet.isReady()) {
        val nc = navController
        nc.navigate(Screen.Startup)
    } else {
        MVIView(CF::recoveryPhraseConfiguration) { model, postIntent ->
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(id = R.string.displayseed_instructions))
                Box(Modifier.padding(top = 16.dp)) {
                    when (model) {
                        is RecoveryPhraseConfiguration.Model.Awaiting -> {
                            Button(onClick = { postIntent(RecoveryPhraseConfiguration.Intent.Decrypt) }) {
                                IconWithText(R.drawable.ic_shield, stringResource(R.string.displayseed_authenticate_button))
                            }
                        }
                        is RecoveryPhraseConfiguration.Model.Decrypting -> {
                            Text(stringResource(id = R.string.displayseed_loading))
                        }
                        is RecoveryPhraseConfiguration.Model.Decrypted -> {
                            Text(model.mnemonics.joinToString(" "), modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
        }
    }
}