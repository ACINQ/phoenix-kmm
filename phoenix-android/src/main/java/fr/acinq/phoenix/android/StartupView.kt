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

import Screen
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import navController
import navigate
import wallet


@Composable
fun StartupView() {
    val nc = navController
    val actualWallet = wallet
    when {
        actualWallet.isLeft -> Text(stringResource(id = R.string.startup_wait))
        actualWallet.isRight && actualWallet.right == null -> nc.navigate(Screen.InitWallet)
        else -> nc.navigate(Screen.Home)
    }
}