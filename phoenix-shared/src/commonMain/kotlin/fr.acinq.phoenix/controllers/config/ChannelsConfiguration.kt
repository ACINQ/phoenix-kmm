package fr.acinq.phoenix.controllers.config

import fr.acinq.phoenix.controllers.MVI

object ChannelsConfiguration {

    data class Model(
        val nodeId: String,
        val json: String,
        val channels: List<Channel>
    ) : MVI.Model() {

        data class Channel(
            val id: String,
            val isOk: Boolean,
            val stateName: String,
            val commitments: Pair<Long, Long>?,
            val json: String,
            val txUrl: String?
        )
    }

    val emptyModel = Model("{}", "", emptyList())

    sealed class Intent: MVI.Intent()

}
