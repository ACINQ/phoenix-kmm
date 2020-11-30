package fr.acinq.phoenix.android

import android.Manifest
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.setContent
import androidx.core.app.ActivityCompat
import androidx.ui.tooling.preview.Devices
import androidx.ui.tooling.preview.Preview
import fr.acinq.phoenix.android.mvi.AppView
import fr.acinq.phoenix.android.mvi.MockView
import fr.acinq.phoenix.ctrl.Content
import fr.acinq.phoenix.ctrl.Initialization
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.acinq.eclair.utils.Either
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.data.Wallet
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

typealias WalletState = Either<UnknownWalletState, Wallet?>
object UnknownWalletState

fun WalletState.isReady() = this.isRight && this.right != null

@ExperimentalCoroutinesApi
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.CAMERA), 1234)
        var wallet by mutableStateOf<WalletState>(Either.Left(UnknownWalletState))
        MainScope().launch {
            (application as PhoenixApplication).business.walletManager.openWalletUpdatesSubscription().consumeEach {
                (application as PhoenixApplication).business.start()
                wallet = Either.Right(it)
                return@consumeEach
            }
        }
        setContent {
            PhoenixAndroidTheme {
                AppView(wallet)
            }
        }
    }
}

val MockModelInitialization = Initialization.Model.Initialization

@Preview(device = Devices.PIXEL_3)
@Composable
fun DefaultPreview() {
    MockView { PhoenixAndroidTheme { InitWallet() } }
}
