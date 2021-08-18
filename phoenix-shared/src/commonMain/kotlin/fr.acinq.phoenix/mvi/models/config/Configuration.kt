package fr.acinq.phoenix.mvi.models.config

import fr.acinq.phoenix.mvi.MVI

object Configuration {

    sealed class Model : MVI.Model() {
        object SimpleMode : Model()
        object FullMode : Model()
    }

    sealed class Intent : MVI.Intent()
}
