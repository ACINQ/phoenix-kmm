package fr.acinq.phoenix.ctrl.config

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.phoenix.ctrl.MVI

typealias ForceCloseChannelsConfigurationController =
        MVI.Controller<ForceCloseChannelsConfiguration.Model, ForceCloseChannelsConfiguration.Intent>

object ForceCloseChannelsConfiguration {

    sealed class Model : MVI.Model() {

        object Loading : Model()
        data class Ready(val channels: List<ChannelInfo>, val address: String) : Model()
        data class ChannelsClosed(val channels: List<ChannelInfo>) : Model()

        data class ChannelInfo(
            val id: ByteVector32,
            val balance: Long, // in sats
            val status: ChannelInfoStatus
        )

        enum class ChannelInfoStatus {
            Normal,
            Offline,
            Syncing,
            Closing,
            Closed,
            Aborted
        }
    }

    sealed class Intent : MVI.Intent() {
        object ForceCloseAllChannels : Intent()
    }
}