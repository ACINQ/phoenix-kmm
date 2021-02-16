package fr.acinq.phoenix.ctrl.config

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.phoenix.ctrl.MVI

typealias CloseChannelsConfigurationController =
        MVI.Controller<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent>

object CloseChannelsConfiguration {

    sealed class Model : MVI.Model() {

        object Loading : Model()
        data class Ready(val channels: List<ChannelInfo>) : Model()
        data class ChannelsClosed(val channels: List<ChannelInfo>) : Model()

        data class ChannelInfo(
            val id: ByteVector32,
            val sats: Long,
            val status: ChannelInfoStatus
        )

        enum class ChannelInfoStatus {
            Normal,
            Closing,
            Closed,
            Aborted
        }
    }

    sealed class Intent : MVI.Intent() {
        data class CloseChannels(val channelIds: List<ByteVector32>, val address: String) : Intent()
        data class ForceCloseChannels(val channelIds: List<ByteVector32>) : Intent()
    }
}