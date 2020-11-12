package fr.acinq.phoenix

import fr.acinq.bitcoin.Block
import fr.acinq.bitcoin.PublicKey
import fr.acinq.eclair.*
import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.blockchain.electrum.ElectrumWatcher
import fr.acinq.eclair.blockchain.fee.OnChainFeeConf
import fr.acinq.eclair.crypto.LocalKeyManager
import fr.acinq.eclair.db.ChannelsDb
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.io.TcpSocket
import fr.acinq.eclair.utils.msat
import fr.acinq.eclair.utils.sat
import fr.acinq.eclair.utils.toByteVector32
import fr.acinq.phoenix.app.*
import fr.acinq.phoenix.app.ctrl.*
import fr.acinq.phoenix.app.ctrl.config.AppChannelsConfigurationController
import fr.acinq.phoenix.app.ctrl.config.AppConfigurationController
import fr.acinq.phoenix.app.ctrl.config.AppDisplayConfigurationController
import fr.acinq.phoenix.app.ctrl.config.AppElectrumConfigurationController
import fr.acinq.phoenix.ctrl.*
import fr.acinq.phoenix.ctrl.config.ChannelsConfigurationController
import fr.acinq.phoenix.ctrl.config.ConfigurationController
import fr.acinq.phoenix.ctrl.config.DisplayConfigurationController
import fr.acinq.phoenix.ctrl.config.ElectrumConfigurationController
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.data.Wallet
import fr.acinq.phoenix.utils.*
import io.ktor.client.*
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.serialization.json.Json
import org.kodein.db.DB
import org.kodein.db.DBFactory
import org.kodein.db.impl.factory
import org.kodein.db.inDir
import org.kodein.db.orm.kotlinx.KotlinxSerializer
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUnsignedTypes::class)
object PhoenixBusiness {

    private fun buildPeer() : Peer {
        val wallet = walletManager.getWallet() ?: error("Wallet must be initialized.")

        val genesisBlock = when (chain) {
            Chain.MAINNET -> Block.LivenetGenesisBlock
            Chain.TESTNET -> Block.TestnetGenesisBlock
            Chain.REGTEST -> Block.RegtestGenesisBlock
        }

        val keyManager = LocalKeyManager(wallet.seed.toByteVector32(), genesisBlock.hash)
        newLogger(loggerFactory).info { "NodeId: ${keyManager.nodeId}" }

        val params = NodeParams(
            keyManager = keyManager,
            alias = "alice",
            features = Features(
                setOf(
                    ActivatedFeature(Feature.OptionDataLossProtect, FeatureSupport.Optional),
                    ActivatedFeature(Feature.VariableLengthOnion, FeatureSupport.Optional)
                )
            ),
            dustLimit = 100.sat,
            onChainFeeConf = OnChainFeeConf(
                maxFeerateMismatch = 10_000.0,
                closeOnOfflineMismatch = true,
                updateFeeMinDiffRatio = 0.1
            ),
            maxHtlcValueInFlightMsat = 150000000L,
            maxAcceptedHtlcs = 100,
            expiryDeltaBlocks = CltvExpiryDelta(144),
            fulfillSafetyBeforeTimeoutBlocks = CltvExpiryDelta(6),
            htlcMinimum = 0.msat,
            minDepthBlocks = 3,
            toRemoteDelayBlocks = CltvExpiryDelta(144),
            maxToLocalDelayBlocks = CltvExpiryDelta(1000),
            feeBase = 546000.msat,
            feeProportionalMillionth = 10,
            reserveToFundingRatio = 0.01, // note: not used (overridden below)
            maxReserveToFundingRatio = 0.05,
            revocationTimeout = 20,
            authTimeout = 10,
            initTimeout = 10,
            pingInterval = 30,
            pingTimeout = 10,
            pingDisconnect = true,
            autoReconnect = false,
            initialRandomReconnectDelay = 5,
            maxReconnectInterval = 3600,
            chainHash = genesisBlock.hash,
            channelFlags = 1,
            paymentRequestExpiry = 3600,
            multiPartPaymentExpiry = 30,
            minFundingSatoshis = 1000.sat,
            maxFundingSatoshis = 16777215.sat,
            maxPaymentAttempts = 5,
            trampolineNode = acinqNodeUri,
            enableTrampolinePayment = true
        )

        val peer = Peer(tcpSocketBuilder, params, acinqNodeUri.id, electrumWatcher, channelsDB, MainScope())

        return peer
    }

    val loggerFactory = LoggerFactory.default
    val tcpSocketBuilder = TcpSocket.Builder()

    val networkMonitor by lazy { NetworkMonitor() }
    val httpClient by lazy {
        HttpClient {
            install(JsonFeature) {
                serializer = KotlinxSerializer(Json {
                    ignoreUnknownKeys = true
                })
            }
        }
    }
    val dbFactory by lazy { DB.factory.inDir(getApplicationFilesDirectoryPath()) }
    val appDB by lazy { dbFactory.open("application", KotlinxSerializer()) }
    val channelsDB by lazy { AppChannelsDB(dbFactory) }

    // RegTest
//    val acinqNodeUri = NodeUri(PublicKey.fromHex("039dc0e0b1d25905e44fdf6f8e89755a5e219685840d0bc1d28d3308f9628a3585"), "localhost", 48001)
//    val chain = Chain.REGTEST

    // TestNet
    val acinqNodeUri = NodeUri(PublicKey.fromHex("03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134"), "13.248.222.197", 9735)
    val chain = Chain.TESTNET

    val masterPubkeyPath = if (chain == Chain.MAINNET) "m/84'/0'/0'" else "m/84'/1'/0'"
    val onChainAddressPath = if (chain == Chain.MAINNET) "m/84'/0'/0'/0/0" else "m/84'/1'/0'/0/0"

    val electrumClient by lazy { ElectrumClient(tcpSocketBuilder, MainScope()) }
    val electrumWatcher by lazy { ElectrumWatcher(electrumClient, MainScope()) }

    val peer by lazy { buildPeer() }

    val walletManager by lazy { WalletManager(appDB) }
    val appHistoryManager by lazy { AppHistoryManager(appDB, peer) }
    val appConfigurationManager by lazy { AppConfigurationManager(appDB, electrumClient, httpClient, chain, loggerFactory) }

    fun newContentController(): ContentController = AppContentController(loggerFactory, walletManager)
    fun newInitController(): InitController = AppInitController(loggerFactory, walletManager)
    fun newHomeController(): HomeController = AppHomeController(loggerFactory, peer, electrumClient, networkMonitor, appHistoryManager)
    fun newReceiveController(): ReceiveController = AppReceiveController(loggerFactory, peer)
    fun newScanController(): ScanController = AppScanController(loggerFactory, peer)
    fun newRestoreWalletController(): RestoreWalletController = AppRestoreWalletController(loggerFactory, walletManager)
    fun newConfigurationController(): ConfigurationController = AppConfigurationController(loggerFactory, walletManager)
    fun newDisplayConfigurationController(): DisplayConfigurationController = AppDisplayConfigurationController(loggerFactory, appConfigurationManager)
    fun newElectrumConfigurationController(): ElectrumConfigurationController = AppElectrumConfigurationController(loggerFactory, appConfigurationManager, chain, masterPubkeyPath, walletManager, electrumClient)
    fun newChannelsConfigurationController(): ChannelsConfigurationController = AppChannelsConfigurationController(loggerFactory, peer, appConfigurationManager, chain)

    fun start() {
        AppConnectionsDaemon(appConfigurationManager, walletManager, networkMonitor, electrumClient, acinqNodeUri, loggerFactory) { peer }
    }
}
