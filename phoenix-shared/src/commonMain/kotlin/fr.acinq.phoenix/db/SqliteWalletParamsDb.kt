package fr.acinq.phoenix.db

import com.squareup.sqldelight.ColumnAdapter
import com.squareup.sqldelight.EnumColumnAdapter
import com.squareup.sqldelight.db.SqlDriver
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PublicKey
import fr.acinq.eclair.*
import fr.acinq.eclair.db.HopDesc
import fr.acinq.eclair.db.IncomingPayment
import fr.acinq.eclair.utils.sat
import fracinqphoenixdb.Incoming_payments
import fracinqphoenixdb.Outgoing_payment_parts
import fracinqphoenixdb.Outgoing_payments
import fracinqphoenixdb.Wallet_params
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.Instant

class SqliteWalletParamsDb(private val driver: SqlDriver) {

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

    private val database = WalletParamsDatabase(
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

    suspend fun getLastWalletParams(): Pair<Instant, WalletParams> {
        return withContext(Dispatchers.Default) {
            queries.list(::mapWalletParams).executeAsList().first()
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