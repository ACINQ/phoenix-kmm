package fr.acinq.phoenix.android

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.acinq.phoenix.android.mvi.Screen
import fr.acinq.phoenix.android.mvi.navController
import fr.acinq.phoenix.android.mvi.navigate
import fr.acinq.phoenix.android.mvi.wallet
import fr.acinq.phoenix.android.utils.logger


@Composable
fun StartupView() {
    val nc = navController
    val actualWallet = wallet
    when {
        actualWallet.isLeft -> Text(stringResource(id = R.string.startup_wait))
        actualWallet.isRight && actualWallet.right == null -> nc.navigate(Screen.InitWallet)
        else -> nc.navigate(Screen.Home)
    }
}