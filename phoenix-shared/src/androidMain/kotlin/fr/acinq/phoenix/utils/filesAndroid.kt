package fr.acinq.phoenix.utils

import android.content.Context
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance


actual fun getApplicationFilesDirectoryPath(): String =
    di.direct.instance<Context>(tag = "app").filesDir.absolutePath
