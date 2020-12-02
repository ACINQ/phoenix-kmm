package fr.acinq.phoenix.ctrl

typealias RestoreWalletController = MVI.Controller<RestoreWallet.Model, RestoreWallet.Intent>

object RestoreWallet {

    sealed class Model : MVI.Model() {
        object Ready : Model()
        data class FilteredWordlist(val words: List<String>) : Model()
        object ValidMnemonics : Model()
        object InvalidMnemonics : Model()
    }

    sealed class Intent : MVI.Intent() {
        data class FilterWordList(val predicate: String) : Intent()
        data class Validate(val mnemonics: List<String>) : Intent()
    }

}
