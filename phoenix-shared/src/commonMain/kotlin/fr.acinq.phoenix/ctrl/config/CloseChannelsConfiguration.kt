package fr.acinq.phoenix.ctrl.config

import fr.acinq.phoenix.ctrl.MVI

typealias CloseChannelsConfigurationController =
        MVI.Controller<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent>

object CloseChannelsConfiguration {

    sealed class Model : MVI.Model() {

        object Loading : Model()
        data class Ready(val channelCount: Int, val sats: Long) : Model()
        object ChannelsClosed : Model()
    }

    sealed class Intent : MVI.Intent() {
        data class CloseAllChannels(val address: String) : Intent()
    }
}