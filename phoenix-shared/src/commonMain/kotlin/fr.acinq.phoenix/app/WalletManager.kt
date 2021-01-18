package fr.acinq.phoenix.app

import fr.acinq.bitcoin.Block
import fr.acinq.eclair.*
import fr.acinq.eclair.blockchain.fee.FeerateTolerance
import fr.acinq.eclair.blockchain.fee.OnChainFeeConf
import fr.acinq.eclair.crypto.LocalKeyManager
import fr.acinq.eclair.utils.msat
import fr.acinq.eclair.utils.sat
import fr.acinq.eclair.utils.toByteVector32
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.data.Wallet
import fr.acinq.phoenix.utils.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalCoroutinesApi::class)
class WalletManager(
    private val chain: Chain
) : CoroutineScope by MainScope() {
    private val _wallet = MutableStateFlow<Wallet?>(null)
    public val walletState: StateFlow<Wallet?> = _wallet
    public val wallet by _wallet

    fun loadWallet(seed: ByteArray): Unit {
        val newWallet = Wallet(seed = seed)
        _wallet.value = newWallet
    }

    suspend fun generateNodeParams(): NodeParams {
        val genesisBlock = when (chain) {
            Chain.MAINNET -> Block.LivenetGenesisBlock
            Chain.TESTNET -> Block.TestnetGenesisBlock
            Chain.REGTEST -> Block.RegtestGenesisBlock
        }

        val keyManager = LocalKeyManager(_wallet.filterNotNull().first().seed.toByteVector32(), genesisBlock.hash)

        return NodeParams(
            keyManager = keyManager,
            alias = "phoenix",
            features = Features(
                setOf(
                    ActivatedFeature(Feature.OptionDataLossProtect, FeatureSupport.Mandatory),
                    ActivatedFeature(Feature.VariableLengthOnion, FeatureSupport.Optional),
                    ActivatedFeature(Feature.PaymentSecret, FeatureSupport.Optional),
                    ActivatedFeature(Feature.BasicMultiPartPayment, FeatureSupport.Optional),
                    ActivatedFeature(Feature.Wumbo, FeatureSupport.Optional),
                    ActivatedFeature(Feature.StaticRemoteKey, FeatureSupport.Optional),
                    ActivatedFeature(Feature.TrampolinePayment, FeatureSupport.Optional),
                    ActivatedFeature(Feature.AnchorOutputs, FeatureSupport.Optional),
                )
            ),
            dustLimit = 546.sat,
            onChainFeeConf = OnChainFeeConf(
                closeOnOfflineMismatch = true,
                updateFeeMinDiffRatio = 0.1,
                feerateTolerance = FeerateTolerance(ratioLow = 0.01, ratioHigh = 100.0)
            ),
            maxHtlcValueInFlightMsat = 150000000L,
            maxAcceptedHtlcs = 30,
            expiryDeltaBlocks = CltvExpiryDelta(144),
            fulfillSafetyBeforeTimeoutBlocks = CltvExpiryDelta(6),
            checkHtlcTimeoutAfterStartupDelaySeconds = 15,
            htlcMinimum = 1000.msat,
            minDepthBlocks = 3,
            toRemoteDelayBlocks = CltvExpiryDelta(144),
            maxToLocalDelayBlocks = CltvExpiryDelta(1000),
            feeBase = 1000.msat,
            feeProportionalMillionth = 10,
            reserveToFundingRatio = 0.01, // note: not used (overridden below)
            maxReserveToFundingRatio = 0.05,
            revocationTimeoutSeconds = 20,
            authTimeoutSeconds = 10,
            initTimeoutSeconds = 10,
            pingIntervalSeconds = 30,
            pingTimeoutSeconds = 10,
            pingDisconnect = true,
            autoReconnect = false,
            initialRandomReconnectDelaySeconds = 5,
            maxReconnectIntervalSeconds = 3600,
            chainHash = genesisBlock.hash,
            channelFlags = 1,
            paymentRequestExpirySeconds = 3600,
            multiPartPaymentExpirySeconds = 60,
            minFundingSatoshis = 1000.sat,
            maxFundingSatoshis = 16777215.sat,
            maxPaymentAttempts = 5,
            enableTrampolinePayment = true
        )
    }
}
