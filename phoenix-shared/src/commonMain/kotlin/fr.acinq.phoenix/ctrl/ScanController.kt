package fr.acinq.phoenix.ctrl

import fr.acinq.phoenix.data.BitcoinUnit


typealias ScanController = MVI.Controller<Scan.Model, Scan.Intent>

object Scan {

    sealed class Model : MVI.Model() {
        object Ready: Model()
        object BadRequest: Model()
        data class DangerousRequest(val request: String): Model()
        data class Validate(val request: String, val amountMsat: Long?, val requestDescription: String?): Model()
        object Sending: Model()
    }

    sealed class Intent : MVI.Intent() {
        data class Parse(val request: String) : Intent()
        data class ConfirmDangerousRequest(val request: String) : Intent()
        data class Send(val request: String, val amount: Double, val unit: BitcoinUnit) : Intent()
    }

}
