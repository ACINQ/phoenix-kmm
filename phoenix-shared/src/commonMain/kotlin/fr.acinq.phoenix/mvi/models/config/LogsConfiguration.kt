package fr.acinq.phoenix.mvi.models.config

import fr.acinq.phoenix.mvi.MVI

object LogsConfiguration {

    sealed class Model : MVI.Model() {
        object Loading : Model()
        data class Ready(val path: String) : Model()
    }

    sealed class Intent : MVI.Intent()

}
