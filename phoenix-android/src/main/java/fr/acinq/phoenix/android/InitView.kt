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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import fr.acinq.phoenix.android.mvi.MVIView
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.ctrl.Initialization
import fr.acinq.phoenix.ctrl.RestoreWallet
import isReady
import navController
import navigate
import wallet


@Composable
fun InitWallet() {
    val nc = navController
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = { nc.navigate(Screen.CreateWallet) }) {
            IconWithText(R.drawable.ic_fire, stringResource(R.string.initwallet_create))
        }
        Button(onClick = { nc.navigate(Screen.RestoreWallet) }) {
            IconWithText(R.drawable.ic_restore, stringResource(R.string.initwallet_restore))
        }
    }
}

@Composable
fun CreateWalletView() {
    val logger = logger()
    if (wallet.isReady()) {
        val nc = navController
        nc.navigate(Screen.Home)
    } else {
        MVIView(CF::initialization) { model, postIntent ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (model) {
                    is Initialization.Model.Initialization -> {
                        Button(onClick = { postIntent(Initialization.Intent.CreateWallet) }) {
                            IconWithText(R.drawable.ic_fire, stringResource(R.string.initwallet_create))
                        }
                    }
                    is Initialization.Model.Creating -> {
                        Text("creating wallet...")
                    }
                }
            }
        }
    }
}

@Composable
fun RestoreWalletView() {
    if (wallet.isReady()) {
        val nc = navController
        nc.navigate(Screen.Home)
    } else {
        MVIView(CF::restoreWallet) { model, postIntent ->
            Column(modifier = Modifier.background(Color.Green)) {
                when (model) {
                    is RestoreWallet.Model.Warning -> {
                        var hasCheckedWarning by remember { mutableStateOf(false) }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.restore_disclaimer_message))
                            Row {
                                Checkbox(hasCheckedWarning, onCheckedChange = { hasCheckedWarning = it })
                                Text(stringResource(R.string.restore_disclaimer_checkbox))
                            }
                            Button(onClick = { postIntent(RestoreWallet.Intent.AcceptWarning) }, enabled = hasCheckedWarning) {
                                IconWithText(R.drawable.ic_arrow_next, stringResource(R.string.restore_disclaimer_next))
                            }
                        }
                    }
                    is RestoreWallet.Model.Ready -> {
                        var wordsInput by remember { mutableStateOf("") }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.restore_instructions))
                            TextField(value = wordsInput, onValueChange = { wordsInput = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
                            Button(onClick = { postIntent(RestoreWallet.Intent.ValidateSeed(wordsInput.split(" "))) }, enabled = wordsInput.isNotBlank()) {
                                IconWithText(R.drawable.ic_check_circle, stringResource(R.string.restore_import_button), iconTint = MaterialTheme.colors.primary)
                            }
                        }
                    }
                    is RestoreWallet.Model.InvalidSeed -> {
                        Text(stringResource(R.string.restore_error))
                    }
                    is RestoreWallet.Model.CreatingWallet -> {
                        Text(stringResource(R.string.restore_in_progress))
                    }
                    else -> {
                        Text("Please hold...")
                    }
                }
            }
        }
    }
}
