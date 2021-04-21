package fr.acinq.phoenix.ctrl

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.Chain


typealias ScanController = MVI.Controller<Scan.Model, Scan.Intent>

object Scan {

    sealed class BadRequestReason {
        data class ChainMismatch(val myChain: Chain, val requestChain: Chain?): BadRequestReason()
        object IsBitcoinAddress: BadRequestReason()
        object UnknownFormat: BadRequestReason()
    }

    sealed class Model : MVI.Model() {
        object Ready: Model()
        data class BadRequest(val reason: BadRequestReason): Model()
        data class DangerousRequest(val request: String): Model()
        data class Validate(
            val request: String,
            val amountMsat: Long?,
            val expiryTimestamp: Long?, // since unix epoch
            val requestDescription: String?,
            val balanceMsat: Long
        ): Model()
        object Sending: Model()
    }

    sealed class Intent : MVI.Intent() {
        data class Parse(val request: String) : Intent()
        data class ConfirmDangerousRequest(val request: String) : Intent()
        data class Send(val request: String, val amount: MilliSatoshi) : Intent()
    }

}
