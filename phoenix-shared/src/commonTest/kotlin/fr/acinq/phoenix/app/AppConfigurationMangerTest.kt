package fr.acinq.phoenix.app

import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.io.TcpSocket
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.data.DisplayedCurrency
import io.ktor.client.*
import io.ktor.client.features.json.*
import kotlinx.coroutines.MainScope
import org.kodein.db.DB
import org.kodein.db.inmemory.inMemory
import org.kodein.db.orm.kotlinx.KotlinxSerializer
import org.kodein.log.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class AppConfigurationMangerTest {

    private val loggerFactory = LoggerFactory.default
    private val tcpSocketBuilder = TcpSocket.Builder()
    private val chain = Chain.REGTEST

    private val db = DB.inMemory.open("application", KotlinxSerializer())
    private val electrumClient by lazy { ElectrumClient(tcpSocketBuilder, MainScope()) }
    private val httpClient by lazy {
        HttpClient {
            install(JsonFeature) {
                serializer = io.ktor.client.features.json.serializer.KotlinxSerializer(kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                })
            }
        }
    }

    private val appConfigurationManager = AppConfigurationManager(db, electrumClient, httpClient, chain, loggerFactory)

    @Test
    fun switchDisplayedCurrency() {
        assertEquals(DisplayedCurrency.BITCOIN, appConfigurationManager.getAppConfiguration().displayedCurrency)
        appConfigurationManager.switchDisplayedCurrency()
        assertEquals(DisplayedCurrency.FIAT, appConfigurationManager.getAppConfiguration().displayedCurrency)
    }
}
