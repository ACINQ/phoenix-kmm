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

package fr.acinq.phoenix.android.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.AmbientContext
import fr.acinq.phoenix.android.PhoenixApplication
import org.kodein.log.Logger
import org.kodein.log.LoggerFactory
import org.kodein.log.frontend.simplePrintFrontend
import org.kodein.log.newLogger


@Composable
fun logger(): Logger {
    val context = AmbientContext.current
    val application = context.applicationContext

    if (application !is PhoenixApplication) { // Preview mode
        return remember { LoggerFactory(simplePrintFrontend).newLogger(context::class) }
    }

    return remember {
        application.business.loggerFactory.newLogger(context::class)
    }
}
