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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.mvi.MVIView
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.ctrl.config.RecoveryPhraseConfiguration
import isReady
import navController
import navigate
import wallet


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