/*
 * Copyright 2021 ACINQ SAS
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

package fr.acinq.phoenix.android.settings


import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.KeyState
import fr.acinq.phoenix.android.utils.Converter
import fr.acinq.phoenix.android.utils.logger

@Composable
fun SeedView(appVM: AppViewModel) {
    val log = logger()
    val ks = keyState
    if (ks !is KeyState.Present) {
        val nc = navController
        nc.navigate(Screen.Startup)
    } else {
        val seed = appVM.decryptSeed()
        Column(modifier = Modifier.padding(16.dp)) {
            AndroidView(factory = {
                TextView(it).apply {
                    text = Converter.html(it.getString(R.string.displayseed_instructions))
                }
            })

            Box(Modifier.padding(top = 16.dp)) {
                if (seed != null) {
                    val words = EncryptedSeed.toMnemonics(seed).joinToString(" ")
                    Text(words, modifier = Modifier.padding(16.dp))
                } else {
                    Text("Could not decrypt the seed :(")
                }
            }
        }
    }
}