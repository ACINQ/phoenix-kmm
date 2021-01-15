package fr.acinq.phoenix.app

import fr.acinq.eclair.WalletParams
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.data.ApiWalletParams
import fr.acinq.phoenix.db.SqliteWalletParamsDb
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class WalletParamsManager(
    loggerFactory: LoggerFactory,
    private val httpClient: HttpClient,
    private val walletParamsDb: SqliteWalletParamsDb,
    private val chain: Chain
) : CoroutineScope by MainScope() {

    private val logger = newLogger(loggerFactory)
    private val mutableWalletParams = MutableStateFlow<WalletParams?>(null)
    val walletParams : StateFlow<WalletParams?> = mutableWalletParams

    public fun start() {
        updateParametersJob = updateLoop()
    }
    public fun stop() {
        launch { updateParametersJob?.cancelAndJoin() }
    }

    var updateParametersJob: Job? = null

    @OptIn(ExperimentalTime::class)
    private fun updateLoop() = launch {
        while (isActive) {
            updateWalletParameters()
            yield()
            delay(5.minutes)
        }
    }

    private suspend fun updateWalletParameters() {
        val apiParams = httpClient.get<ApiWalletParams>("https://acinq.co/phoenix/walletcontext.json")
        val newWalletParams = apiParams.export(chain)
        logger.debug { "retrieved WalletParams=${newWalletParams}" }
        walletParamsDb.setWalletParams(newWalletParams)
        mutableWalletParams.value = newWalletParams
    }
}