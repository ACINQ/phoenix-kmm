package fr.acinq.phoenix.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.setContent
import androidx.ui.tooling.preview.Devices
import androidx.ui.tooling.preview.Preview
import fr.acinq.phoenix.android.mvi.MVIView
import fr.acinq.phoenix.ctrl.Init
import fr.acinq.phoenix.ctrl.InitController
import org.kodein.di.DIAware
import org.kodein.di.android.di
import org.kodein.type.generic

class MainActivity : AppCompatActivity(), DIAware {
    override val di by di()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PhoenixAndroidTheme {
                MVIView(di, generic<InitController>()) { m, i -> InitView(m, i) }
            }
        }
    }
}

@Composable
fun InitView(model: Init.Model, postIntent: (Init.Intent) -> Unit) {
    Text(model.toString())
}

@Preview(device = Devices.PIXEL_3)
@Composable
fun DefaultPreview() {
    InitView(Init.Model.Initialization) {}
}
