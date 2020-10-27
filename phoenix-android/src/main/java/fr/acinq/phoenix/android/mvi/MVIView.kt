package fr.acinq.phoenix.android.mvi

import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.viewModel
import fr.acinq.phoenix.ctrl.MVI
import org.kodein.di.DI
import org.kodein.type.TypeToken


@Composable
fun <M : MVI.Model, I : MVI.Intent> MVIView(
    di: DI,
    controllerType: TypeToken<MVI.Controller<M, I>>,
    content: @Composable (model: M, postIntent: (I) -> Unit) -> Unit
) {
    val controller: MVI.Controller<M, I> by viewModel(factory = MVIControllerViewModel.Factory(di, controllerType))

    var state by remember { mutableStateOf(controller.firstModel) }

    onActive {
        val unsubscribe = controller.subscribe { state = it }
        onDispose {
            unsubscribe()
        }
    }

    content(state) { controller.intent(it) }
}

