package fr.acinq.phoenix.ctrl.config

import fr.acinq.phoenix.ctrl.MVI
import fr.acinq.tor.Tor

typealias TorConfigurationController = MVI.Controller<TorConfiguration.Model, TorConfiguration.Intent>

object TorConfiguration {
    data class Model(val info: Tor.TorInfo?) : MVI.Model()
    sealed class Intent : MVI.Intent()
}
