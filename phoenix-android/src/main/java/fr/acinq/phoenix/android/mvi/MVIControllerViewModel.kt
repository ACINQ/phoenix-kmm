package fr.acinq.phoenix.android.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import fr.acinq.phoenix.ctrl.ControllerFactory
import fr.acinq.phoenix.ctrl.MVI
import org.kodein.log.Logger
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class MVIControllerViewModel<M : MVI.Model, I : MVI.Intent>(val controller: MVI.Controller<M, I>) : ViewModel() {
    val logger: Logger = newLogger(LoggerFactory.default)

    override fun onCleared() {
        logger.verbose { "clearing controller view model with controller=$controller" }
        controller.stop()
    }

    class Factory<M : MVI.Model, I : MVI.Intent>(
        private val controllerFactory: ControllerFactory,
        private val getController: ControllerFactory.() -> MVI.Controller<M, I>
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MVIControllerViewModel(controllerFactory.getController()) as T
        }
    }

}
