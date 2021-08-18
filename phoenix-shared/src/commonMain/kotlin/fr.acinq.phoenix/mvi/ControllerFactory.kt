package fr.acinq.phoenix.mvi

import fr.acinq.phoenix.mvi.models.*
import fr.acinq.phoenix.mvi.models.config.*

typealias ContentController = MVI.Controller<Content.Model, Content.Intent>
typealias HomeController = MVI.Controller<Home.Model, Home.Intent>
typealias InitializationController = MVI.Controller<Initialization.Model, Initialization.Intent>
typealias ReceiveController = MVI.Controller<Receive.Model, Receive.Intent>
typealias ScanController = MVI.Controller<Scan.Model, Scan.Intent>
typealias RestoreWalletController = MVI.Controller<RestoreWallet.Model, RestoreWallet.Intent>

typealias ChannelsConfigurationController = MVI.Controller<ChannelsConfiguration.Model, ChannelsConfiguration.Intent>
typealias CloseChannelsConfigurationController = MVI.Controller<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent>
typealias ConfigurationController = MVI.Controller<Configuration.Model, Configuration.Intent>
typealias ElectrumConfigurationController = MVI.Controller<ElectrumConfiguration.Model, ElectrumConfiguration.Intent>
typealias LogsConfigurationController = MVI.Controller<LogsConfiguration.Model, LogsConfiguration.Intent>

interface ControllerFactory {
    fun content(): ContentController
    fun initialization(): InitializationController
    fun home(): HomeController
    fun receive(): ReceiveController
    fun scan(firstModel: Scan.Model = Scan.Model.Ready): ScanController
    fun restoreWallet(): RestoreWalletController
    fun configuration(): ConfigurationController
    fun electrumConfiguration(): ElectrumConfigurationController
    fun channelsConfiguration(): ChannelsConfigurationController
    fun logsConfiguration(): LogsConfigurationController
    fun closeChannelsConfiguration(): CloseChannelsConfigurationController
    fun forceCloseChannelsConfiguration(): CloseChannelsConfigurationController
}
