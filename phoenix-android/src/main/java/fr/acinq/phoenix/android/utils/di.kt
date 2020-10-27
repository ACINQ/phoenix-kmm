package fr.acinq.phoenix.android.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ContextAmbient
import org.kodein.di.DI
import org.kodein.di.android.closestDI


@Composable
fun di(): DI {
    val di by closestDI(ContextAmbient.current)
    return di
}
