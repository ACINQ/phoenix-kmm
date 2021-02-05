package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.phoenix.app.ctrl.AppController
import fr.acinq.phoenix.ctrl.config.TorConfiguration
import fr.acinq.tor.Tor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

@OptIn(ExperimentalCoroutinesApi::class)
class AppTorConfigurationController(loggerFactory: LoggerFactory, private val tor: Tor) : AppController<TorConfiguration.Model, TorConfiguration.Intent>(loggerFactory, TorConfiguration.Model(null)) {

    init {
        launch {
            tor.info.collect {
                model(TorConfiguration.Model(it))
            }
        }
    }

    override fun process(intent: TorConfiguration.Intent) {}
}
