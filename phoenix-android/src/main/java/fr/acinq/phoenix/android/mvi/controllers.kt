package fr.acinq.phoenix.android.mvi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Providers
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticAmbientOf
import androidx.compose.ui.platform.ContextAmbient
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.*
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.ctrl.*
import fr.acinq.phoenix.ctrl.config.*
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


sealed class Screen(val route: String, val arg: String? = null) {
    val fullRoute by lazy { if (arg.isNullOrBlank()) route else "${route}/{$arg}" }

    object InitWallet : Screen("initwallet")
    object CreateWallet : Screen("createwallet")
    object RestoreWallet : Screen("restorewallet")
    object Startup : Screen("startup")
    object Home : Screen("home")
    object Settings : Screen("settings")
    object DisplaySeed : Screen("settings/seed")
    object Receive : Screen("receive")
    object ReadData : Screen("readdata")
    object Send : Screen("send", "request")
}

@Composable
fun requireWallet(
    from: Screen,
    children: @Composable () -> Unit
) {
    val wallet = application.business.walletManager.getWallet()
    if (from !is Screen.CreateWallet && wallet == null) {
        logger().warning { "valid wallet is required on screen=$from" }
        navController.navigate(Screen.CreateWallet)
        Text("redirecting...")
    } else {
        logger().verbose { "creating screen=$from..." }
        children()
    }
}

@Composable
private fun currentRoute(): String? {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    return navBackStackEntry?.arguments?.getString(KEY_ROUTE)
}

typealias CF = ControllerFactory

val ControllerFactoryAmbient = staticAmbientOf<ControllerFactory?>(null)
val NavControllerAmbient = staticAmbientOf<NavHostController?>(null)
val WalletStateAmbient = staticAmbientOf<WalletState>(null)

@Composable
val controllerFactory: ControllerFactory
    get() = ControllerFactoryAmbient.current ?: error("No controller factory set. Please use appView or mockView.")

@Composable
val navController: NavHostController
    get() = NavControllerAmbient.current ?: error("no navigation controller defined")

@Composable
val wallet: WalletState get() = WalletStateAmbient.current

public fun NavController.navigate(screen: Screen, arg: String? = null, builder: NavOptionsBuilder.() -> Unit = {}) {
    val route = if (arg.isNullOrBlank()) screen.route else "${screen.route}/$arg"
    newLogger<NavController>(LoggerFactory.default).verbose { "navigating to $route" }
    try {
        navigate(route, builder)
    } catch (e: Exception) {
        newLogger(LoggerFactory.default).error(e) { "failed to navigate to $route" }
    }
}

@Composable
val application: PhoenixApplication
    get() = ContextAmbient.current.applicationContext as? PhoenixApplication
        ?: error("Application is not of type PhoenixApplication. Are you using appView in preview?")

@ExperimentalMaterialApi
@Composable
fun AppView(wallet: WalletState) {
    val navController = rememberNavController()
    Providers(
        ControllerFactoryAmbient provides application.business.controllers,
        NavControllerAmbient provides navController,
        WalletStateAmbient provides wallet
    ) {
        Column {
            Text(currentRoute().toString())
            NavHost(navController = navController, startDestination = Screen.Startup.route) {
                composable(Screen.Startup.fullRoute) {
                    StartupView()
                }
                composable(Screen.InitWallet.fullRoute) {
                    InitWallet()
                }
                composable(Screen.CreateWallet.fullRoute) {
                    CreateWalletView()
                }
                composable(Screen.RestoreWallet.fullRoute) {
                    RestoreWalletView()
                }
                composable(Screen.Home.fullRoute) {
                    HomeView()
                }
                composable(Screen.Receive.fullRoute) {
                    ReceiveView()
                }
                composable(Screen.ReadData.fullRoute) {
                    ReadDataView()
                }
                composable(Screen.Send.fullRoute) { backStackEntry ->
                    SendView(backStackEntry.arguments?.getString("request")?.run {
                        PaymentRequest.read(cleanUpInvoice(this))
                    })
                }
                composable(Screen.Settings.fullRoute) {
                    SettingsView()
                }
                composable(Screen.DisplaySeed.fullRoute) {
                    SeedView()
                }
            }
        }
    }
}

private fun cleanUpInvoice(input: String): String {
    val trimmed = input.replace("\\u00A0", "").trim()
    return when {
        trimmed.startsWith("lightning://", true) -> trimmed.drop(12)
        trimmed.startsWith("lightning:", true) -> trimmed.drop(10)
        trimmed.startsWith("bitcoin://", true) -> trimmed.drop(10)
        trimmed.startsWith("bitcoin:", true) -> trimmed.drop(8)
        else -> trimmed
    }
}




@Suppress("UNREACHABLE_CODE")
val MockControllers = object : ControllerFactory {
    override fun initialization(): InitializationController = MVI.Controller.Mock(MockModelInitialization)
    override fun content(): ContentController = MVI.Controller.Mock(TODO())
    override fun home(): HomeController = MVI.Controller.Mock(TODO())
    override fun receive(): ReceiveController = MVI.Controller.Mock(TODO())
    override fun scan(): ScanController = MVI.Controller.Mock(TODO())
    override fun restoreWallet(): RestoreWalletController = MVI.Controller.Mock(TODO())
    override fun configuration(): ConfigurationController = MVI.Controller.Mock(TODO())
    override fun displayConfiguration(): DisplayConfigurationController = MVI.Controller.Mock(TODO())
    override fun electrumConfiguration(): ElectrumConfigurationController = MVI.Controller.Mock(TODO())
    override fun channelsConfiguration(): ChannelsConfigurationController = MVI.Controller.Mock(TODO())
    override fun recoveryPhraseConfiguration(): RecoveryPhraseConfigurationController {
        TODO("Not yet implemented")
    }
}

@Composable
fun MockView(children: @Composable () -> Unit) {
    Providers(ControllerFactoryAmbient provides MockControllers) {
        children()
    }
}
