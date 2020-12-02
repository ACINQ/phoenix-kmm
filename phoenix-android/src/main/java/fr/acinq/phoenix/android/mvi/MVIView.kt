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

package fr.acinq.phoenix.android.mvi

import ControllerFactoryAmbient
import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.viewModel
import controllerFactory
import fr.acinq.phoenix.android.utils.isPreview
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.ctrl.ControllerFactory
import fr.acinq.phoenix.ctrl.MVI

@Composable
fun <M : MVI.Model, I : MVI.Intent> MVIView(
    getController: ControllerFactory.() -> MVI.Controller<M, I>,
    children: @Composable (model: M, postIntent: (I) -> Unit) -> Unit
) {
    val logger = logger()
    // Gets the MVI controller or create it if it does not already exist.
    // Note that the controller is stored inside a ViewModel, which means that it will survive configuration changes activity restarts.
    val controller = if (!isPreview) {
        val viewModel: MVIControllerViewModel<M, I> = viewModel(factory = MVIControllerViewModel.Factory(controllerFactory, getController))
        viewModel.controller
    } else {
        val cf = ControllerFactoryAmbient.current ?: MockControllers
        remember { cf.getController() }
    }

    var model by remember { mutableStateOf(controller.firstModel) }

    // Subscribes to the controller when the view becomes active (first display on screen),
    // unsubscribes when the view becomes inactive.
    // Being subscribed to the controller means we will receive new models when the controller has a new model.
    // Note that the controller will immediately send the latest available model upon subscription.
    onActive {
        val unsubscribe = controller.subscribe { model = it }
        onDispose { unsubscribe() }
    }

    // Display content according to model.
    children(model) { controller.intent(it) }
}
