package fr.acinq.phoenix.ctrl

import fr.acinq.phoenix.ctrl.config.ChannelsConfigurationController
import fr.acinq.phoenix.ctrl.config.CloseChannelsConfigurationController
import fr.acinq.phoenix.ctrl.config.ConfigurationController
import fr.acinq.phoenix.ctrl.config.ElectrumConfigurationController


interface ControllerFactory {
    fun content(): ContentController
    fun initialization(): InitializationController
    fun home(): HomeController
    fun receive(): ReceiveController
    fun scan(): ScanController
    fun restoreWallet(): RestoreWalletController
    fun configuration(): ConfigurationController
    fun electrumConfiguration(): ElectrumConfigurationController
    fun channelsConfiguration(): ChannelsConfigurationController
    fun closeChannelsConfiguration(): CloseChannelsConfigurationController
}
