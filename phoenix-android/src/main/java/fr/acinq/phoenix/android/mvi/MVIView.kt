package fr.acinq.phoenix.android.mvi

import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.viewModel
import fr.acinq.phoenix.android.utils.di
import fr.acinq.phoenix.ctrl.MVI
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.type.TypeToken
import org.kodein.type.generic


@Composable
fun <M : MVI.Model, I : MVI.Intent> mviView(
    di: DI,
    controllerType: TypeToken<MVI.Controller<M, I>>,
    content: @Composable (model: M, postIntent: (I) -> Unit) -> Unit
) {
    val controller: MVI.Controller<M, I> by viewModel(factory = MVIControllerViewModel.Factory(di, controllerType))

    var state by remember { mutableStateOf(controller.firstModel) }

    onActive {
        val unsubscribe = controller.subscribe { state = it }
        onDispose { unsubscribe() }
    }

    content(state) { controller.intent(it) }
}

inline class MVIContext<C : MVI.Controller<*, *>>(val controllerType: TypeToken<C>)

inline fun <reified C : MVI.Controller<*, *>> mvi(): MVIContext<C> = MVIContext(generic())

@Composable
fun <M : MVI.Model, I : MVI.Intent, C : MVI.Controller<M, I>> MVIContext<C>.View(content: @Composable (model: M, postIntent: (I) -> Unit) -> Unit) {
    @Suppress("UNCHECKED_CAST")
    mviView(di(), controllerType as TypeToken<MVI.Controller<M, I>>, content)
}
