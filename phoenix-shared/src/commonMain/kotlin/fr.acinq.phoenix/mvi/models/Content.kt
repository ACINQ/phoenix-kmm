package fr.acinq.phoenix.mvi.models

import fr.acinq.phoenix.mvi.MVI

object Content {

    sealed class Model : MVI.Model() {
        object Waiting : Model()
        object IsInitialized : Model()
        object NeedInitialization : Model()
    }

    sealed class Intent : MVI.Intent()

}
