package fr.acinq.phoenix.ctrl

typealias InitializationController = MVI.Controller<Initialization.Model, Initialization.Intent>

object Initialization {

    sealed class Model : MVI.Model() {
        object Ready : Model()
        data class GeneratedMnemonics(val mnemonics: List<String>) : Model()
    }

    sealed class Intent : MVI.Intent() {
        data class GenerateMnemonics(val seed: ByteArray) : Intent()
    }

}
