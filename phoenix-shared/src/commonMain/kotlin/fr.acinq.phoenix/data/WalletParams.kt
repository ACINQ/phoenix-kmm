package fr.acinq.phoenix.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WalletParams(val testnet: ChainParams, val mainnet: ChainParams)

@Serializable
data class ChainParams(
    val version : Int,
    @SerialName("latest_critical_version") val latestCriticalVersion : Int,
    val trampoline: TrampolineParams,
)

@Serializable
data class TrampolineParams(val v2: V2) {
    @Serializable
    data class TrampolineFees(
        @SerialName("fee_base_sat") val feeBaseSat: Int,
        @SerialName("fee_per_millionths") val feePerMillionths: Int,
        @SerialName("cltv_expiry") val cltvExpiry: Int,
    )
    @Serializable
    data class NodeUri(val name: String, val uri: String)
    @Serializable
    data class V2(val attempts: List<TrampolineFees>, val nodes: List<NodeUri> = emptyList())
}