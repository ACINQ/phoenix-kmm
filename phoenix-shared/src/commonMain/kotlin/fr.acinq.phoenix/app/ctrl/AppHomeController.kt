package fr.acinq.phoenix.app.ctrl

import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.io.Peer
import fr.acinq.phoenix.app.AppConfigurationManager
import fr.acinq.phoenix.app.AppHistoryManager
import fr.acinq.phoenix.app.CurrencyConverter
import fr.acinq.phoenix.ctrl.Home
import fr.acinq.phoenix.data.CurrencyUnit
import fr.acinq.phoenix.data.Transaction
import fr.acinq.phoenix.utils.NetworkMonitor
import fr.acinq.phoenix.utils.localCommitmentSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory


@OptIn(ExperimentalCoroutinesApi::class)
class AppHomeController(
    loggerFactory: LoggerFactory,
    private val peer: Peer,
    private val electrumClient: ElectrumClient,
    private val networkMonitor: NetworkMonitor,
    private val historyManager: AppHistoryManager,
    private val appConfigurationManager: AppConfigurationManager,
    private val currencyConverter: CurrencyConverter
) : AppController<Home.Model, Home.Intent>(loggerFactory, Home.emptyModel) {

    private val displayedCurrency: CurrencyUnit get() = appConfigurationManager.getAppConfiguration().displayedCurrency

    init {
        launch {
            peer.connectionState.collect {
                model { copy(connections = connections.copy(peer = it)) }
            }
        }
        launch {
            electrumClient.connectionState.collect {
                model { copy(connections = connections.copy(electrum = it)) }
            }
        }
        launch {
            networkMonitor.openNetworkStateSubscription().consumeEach {
                model { copy(connections = connections.copy(internet = it)) }
            }
        }

        launch {
            peer.channelsFlow.collect { channels ->
                val balance = currencyConverter.convert(
                    amountMsat = channels.values.sumOf { it.localCommitmentSpec?.toLocal?.msat ?: 0 },
                    to = displayedCurrency
                )
                model {
                    copy(
                        balance = balance,
                        displayedCurrency = displayedCurrency
                    )
                }
            }
        }

        launch {
            historyManager.transactions
                .collectIndexed { nth, list ->
                    val modelList = list.map {
                        it.copy(displayedAmount = currencyConverter.convert(it.amountMsat, displayedCurrency))
                    }

                    model {
                        val lastTransaction = modelList.firstOrNull()
                        if (nth != 0 && lastTransaction != null && lastTransaction.status != Transaction.Status.Pending) {
                            copy(history = modelList, lastTransaction = lastTransaction)
                        } else {
                            copy(history = modelList)
                        }
                    }
                }
        }
    }

    override fun process(intent: Home.Intent) {}

}
