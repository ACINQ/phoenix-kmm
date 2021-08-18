package fr.acinq.phoenix.mvi.models

import fr.acinq.phoenix.mvi.MVI

object Initialization {

    sealed class Model : MVI.Model() {
        object Ready : Model()
        data class GeneratedWallet(val mnemonics: List<String>, val seed: ByteArray) : Model() {
            override fun toString() = "GeneratedWallet"
        }
    }

    sealed class Intent : MVI.Intent() {
        data class GenerateWallet(val entropy: ByteArray) : Intent() {
            override fun toString() = "GenerateWallet"
        }
    }

}
