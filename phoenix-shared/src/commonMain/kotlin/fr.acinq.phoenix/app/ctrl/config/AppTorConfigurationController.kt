package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.utils.Connection
import fr.acinq.phoenix.app.AppConfigurationManager
import fr.acinq.phoenix.app.TcpConnectionManager
import fr.acinq.phoenix.app.WalletManager
import fr.acinq.phoenix.app.ctrl.AppController
import fr.acinq.phoenix.ctrl.config.ElectrumConfiguration
import fr.acinq.phoenix.ctrl.config.TorConfiguration
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.data.InvalidElectrumAddress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.math.log

@OptIn(ExperimentalCoroutinesApi::class)
class AppTorConfigurationController(loggerFactory: LoggerFactory, private val tcpConnectionManager: TcpConnectionManager) : AppController<TorConfiguration.Model, TorConfiguration.Intent>(loggerFactory, TorConfiguration.Model(null)) {

    init {
        launch {
            tcpConnectionManager.tor.info.collect {
                model(TorConfiguration.Model(it))
            }
        }
    }

    override fun process(intent: TorConfiguration.Intent) {}
}
