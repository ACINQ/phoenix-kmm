package fr.acinq.phoenix.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.kodein.db.Index
import org.kodein.db.indexSet
import org.kodein.db.model.orm.Metadata


@Serializable
data class Transaction(
    override val id: String,
    val amountMsat: Long,
    @Transient val displayedAmount: Double = 0.0, // Unit depends on the app configuration
    val desc: String, // Swift does not support fields named description
    val status: Status,
    val timestamp: Long
//    val paymentHash: String,
//    val paymentRequest: String,
//    val paymentPreimage: String,
//    val creationTimestamp: Long,
//    val expirationTimestamp: Long,
//    val completionTimestamp: Long
) : Metadata {

    override fun indexes(): Set<Index> = indexSet("timestamp" to timestamp)

    enum class Status { Success, Pending, Failure }

}
