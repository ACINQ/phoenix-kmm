package fr.acinq.phoenix.db

import com.squareup.sqldelight.ColumnAdapter
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import fr.acinq.bitcoin.PublicKey
import fr.acinq.eclair.CltvExpiryDelta
import fr.acinq.eclair.NodeUri
import fr.acinq.eclair.TrampolineFees
import fr.acinq.eclair.WalletParams
import fr.acinq.eclair.utils.sat
import fracinqphoenixdb.Wallet_params
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class SqliteAppDb(driver: SqlDriver) {

    private val trampolineFeesAdapter: ColumnAdapter<List<TrampolineFees>, String> = object : ColumnAdapter<List<TrampolineFees>, String> {
        override fun decode(databaseValue: String): List<TrampolineFees> = databaseValue.split(";").map { fees ->
            val els = fees.split(":")
            val feeBase = els[0].toLong()
            val feeProportional = els[1].toLong()
            val cltvEpiry = els[2].toInt()
            TrampolineFees(feeBase.sat, feeProportional, CltvExpiryDelta(cltvEpiry))
        }

        override fun encode(value: List<TrampolineFees>): String = value.joinToString(";") {
            "${it.feeBase.sat}:${it.feeProportional}:${it.cltvExpiryDelta.toInt()}"
        }
    }

    private val database = AppDatabase(
        driver = driver,
        wallet_paramsAdapter = Wallet_params.Adapter(trampoline_feesAdapter = trampolineFeesAdapter)
    )

    private val queries = database.walletParamsQueries

    suspend fun setWalletParams(walletParams: WalletParams) {
        withContext(Dispatchers.Default) {
            queries.transaction {
                if (queries.get(walletParams.trampolineNode.id.toString()).executeAsOneOrNull() == null) {
                    queries.insert(
                        node_id = walletParams.trampolineNode.id.toString(),
                        node_host = walletParams.trampolineNode.host,
                        node_port = walletParams.trampolineNode.port,
                        trampoline_fees = walletParams.trampolineFees,
                        updated_at = Clock.System.now().epochSeconds
                    )
                } else {
                    queries.update(
                        node_host = walletParams.trampolineNode.host,
                        node_port = walletParams.trampolineNode.port,
                        trampoline_fees = walletParams.trampolineFees,
                        updated_at = Clock.System.now().epochSeconds,
                        // WHERE
                        node_id = walletParams.trampolineNode.id.toString(),
                    )
                }
            }
        }
    }

    fun getWalletParamsList(): Flow<List<Pair<Instant, WalletParams>>> =
        queries.list(::mapWalletParams).asFlow().mapToList()

    suspend fun getFirstWalletParamsOrNull(): Pair<Instant, WalletParams?> {
        return withContext(Dispatchers.Default) {
            queries.list(::mapWalletParams).executeAsList().firstOrNull() ?: Instant.DISTANT_PAST to null
        }
    }

    private fun mapWalletParams(
         node_id: String,
         node_host: String,
         node_port: Int,
         trampoline_fees: List<TrampolineFees>,
         updated_at: Long
    ): Pair<Instant, WalletParams> {
        return Instant.fromEpochSeconds(updated_at) to WalletParams(NodeUri(PublicKey.fromHex(node_id), node_host, node_port), trampoline_fees)
    }

}