package fr.acinq.phoenix

import fr.acinq.bitcoin.Block
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.bitcoin.PublicKey
import fr.acinq.eclair.*
import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.blockchain.electrum.ElectrumWatcher
import fr.acinq.eclair.blockchain.fee.FeerateTolerance
import fr.acinq.eclair.blockchain.fee.OnChainFeeConf
import fr.acinq.eclair.crypto.LocalKeyManager
import fr.acinq.eclair.db.ChannelsDb
import fr.acinq.eclair.db.Databases
import fr.acinq.eclair.db.PaymentsDb
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.io.TcpSocket
import fr.acinq.eclair.utils.msat
import fr.acinq.eclair.utils.sat
import fr.acinq.eclair.utils.setEclairLoggerFactory
import fr.acinq.eclair.utils.toByteVector32
import fr.acinq.phoenix.app.*
import fr.acinq.phoenix.app.ctrl.*
import fr.acinq.phoenix.app.ctrl.config.*
import fr.acinq.phoenix.ctrl.*
import fr.acinq.phoenix.ctrl.config.*
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.db.*
import fr.acinq.phoenix.db.SqliteChannelsDb
import fr.acinq.phoenix.utils.*
import io.ktor.client.*
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.kodein.db.DB
import org.kodein.db.impl.factory
import org.kodein.db.inDir
import org.kodein.db.orm.kotlinx.KotlinxSerializer
import org.kodein.log.LoggerFactory
import org.kodein.log.frontend.defaultLogFrontend
import org.kodein.log.newLogger
import org.kodein.log.withShortPackageKeepLast
import org.kodein.memory.file.Path
import org.kodein.memory.file.resolve


@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUnsignedTypes::class)
class PhoenixBusiness(private val ctx: PlatformContext) {

    private val logMemory = LogMemory(Path(getApplicationFilesDirectoryPath(ctx)).resolve("logs"))

    val loggerFactory = LoggerFactory(
        defaultLogFrontend.withShortPackageKeepLast(1),
        logMemory.withShortPackageKeepLast(1)
    )

    private val tcpSocketBuilder = TcpSocket.Builder()

    private val networkMonitor by lazy { NetworkMonitor(loggerFactory, ctx) }
    private val httpClient by lazy {
        HttpClient {
            install(JsonFeature) {
                serializer = KotlinxSerializer(Json {
                    ignoreUnknownKeys = true
                })
            }
        }
    }
    private val dbFactory by lazy { DB.factory.inDir(getApplicationFilesDirectoryPath(ctx)) }
    private val appDB by lazy { dbFactory.open("application", KotlinxSerializer()) }
    private val channelsDb by lazy { SqliteChannelsDb(createChannelsDbDriver(ctx), peer.nodeParams) }
    private val paymentsDb by lazy { SqlitePaymentsDb(createPaymentsDbDriver(ctx)) }
    private val walletParamsDb by lazy { SqliteWalletParamsDb(createWalletParamsDbDriver(ctx)) }

    // TestNet
    private val chain = Chain.TESTNET

    private val masterPubkeyPath = if (chain == Chain.MAINNET) "m/84'/0'/0'" else "m/84'/1'/0'"

    private val electrumClient by lazy { ElectrumClient(tcpSocketBuilder, MainScope()) }
    private val electrumWatcher by lazy { ElectrumWatcher(electrumClient, MainScope()) }

    private var appConnectionsDaemon: AppConnectionsDaemon? = null

    private val walletManager by lazy { WalletManager() }
    private val walletParamsManager by lazy { WalletParamsManager(loggerFactory, httpClient, walletParamsDb, chain) }
    private val peerManager by lazy { PeerManager(loggerFactory, channelsDb, paymentsDb, walletManager, walletParamsManager, tcpSocketBuilder, electrumWatcher, chain) }
    private val appHistoryManager by lazy { AppHistoryManager(loggerFactory, appDB, peerManager) }
    private val appConfigurationManager by lazy { AppConfigurationManager(appDB, electrumClient, chain, loggerFactory) }

    val currencyManager by lazy { CurrencyManager(loggerFactory, appDB, httpClient) }
    val connectionsMonitor by lazy { ConnectionsMonitor(peerManager, electrumClient, networkMonitor) }
    val util by lazy { Utilities(loggerFactory, chain) }

    init {
        setEclairLoggerFactory(loggerFactory)
    }

    fun start() {
        if (appConnectionsDaemon == null) {
            appConnectionsDaemon = AppConnectionsDaemon(
                appConfigurationManager,
                walletManager,
                currencyManager,
                networkMonitor,
                electrumClient,
                loggerFactory,
                getPeer = { peer } // lazy getter
            )
    }

    // Converts a mnemonics list to a seed.
    // This is generally called with a mnemonics list that has been previously saved.
    fun prepWallet(mnemonics: List<String>, passphrase: String = ""): ByteArray {
        MnemonicCode.validate(mnemonics)
        return MnemonicCode.toSeed(mnemonics, passphrase)
    }

    fun loadWallet(seed: ByteArray): Unit {
        if (walletManager.wallet == null) {
            walletManager.loadWallet(seed)
        }
    }

    fun incrementDisconnectCount(): Unit {
        appConnectionsDaemon?.incrementDisconnectCount()
    }

    fun decrementDisconnectCount(): Unit {
        appConnectionsDaemon?.decrementDisconnectCount()
    }

    fun nodeID(): String {
        return peer.nodeParams.nodeId.toString()
    }

    // The (node_id, fcm_token) tuple only needs to be registered once.
    // And after that, only if the tuple changes (e.g. different fcm_token).
    fun registerFcmToken(token: String?) {
        peer.registerFcmToken(token)
    }

    fun incomingTransactionFlow() =
        appHistoryManager.openIncomingTransactionSubscription().consumeAsFlow()

    val controllers: ControllerFactory = object : ControllerFactory {
        override fun content(): ContentController = AppContentController(loggerFactory, walletManager)
        override fun initialization(): InitializationController = AppInitController(loggerFactory, walletManager)
        override fun home(): HomeController = AppHomeController(loggerFactory, peerManager, appHistoryManager)
        override fun receive(): ReceiveController = AppReceiveController(loggerFactory, peerManager)
        override fun scan(): ScanController = AppScanController(loggerFactory, peerManager)
        override fun restoreWallet(): RestoreWalletController = AppRestoreWalletController(loggerFactory, walletManager)
        override fun configuration(): ConfigurationController = AppConfigurationController(loggerFactory, walletManager)
        override fun electrumConfiguration(): ElectrumConfigurationController = AppElectrumConfigurationController(loggerFactory, appConfigurationManager, chain, masterPubkeyPath, walletManager, electrumClient)
        override fun channelsConfiguration(): ChannelsConfigurationController = AppChannelsConfigurationController(loggerFactory, peerManager, chain)
        override fun logsConfiguration(): LogsConfigurationController = AppLogsConfigurationController(ctx, loggerFactory, logMemory)
        override fun closeChannelsConfiguration(): CloseChannelsConfigurationController = AppCloseChannelsConfigurationController(loggerFactory, peerManager, util)
    }
}
