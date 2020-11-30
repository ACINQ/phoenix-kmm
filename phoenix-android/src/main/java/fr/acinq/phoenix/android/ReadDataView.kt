package fr.acinq.phoenix.android

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.material.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ContextAmbient
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ui.tooling.preview.Preview
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import fr.acinq.phoenix.android.mvi.Screen
import fr.acinq.phoenix.android.mvi.navController
import fr.acinq.phoenix.android.mvi.navigate
import fr.acinq.phoenix.android.mvi.requireWallet

@Composable
fun ReadDataView() {
    requireWallet(from = Screen.ReadData) {
        val context = ContextAmbient.current.applicationContext
        val nc = navController
        var scanned by remember { mutableStateOf("") }
        Box(modifier = Modifier) {
            Button({}) {
                IconWithText(icon = R.drawable.ic_clipboard, text = stringResource(id = R.string.send_init_paste))
            }
            Button({}) {
                IconWithText(icon = R.drawable.ic_arrow_next, text = stringResource(id = R.string.btn_cancel))
            }
            AndroidView(viewBlock = {
                DecoratedBarcodeView(context)
            }) { view ->
                view.initializeFromIntent(Intent().apply {
                    putExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
                    putExtra(Intents.Scan.FORMATS, BarcodeFormat.QR_CODE.name)
                })
                view.decodeContinuous(object : BarcodeCallback {
                    override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) = Unit
                    override fun barcodeResult(result: BarcodeResult?) {
                        result?.text?.let {
                            scanned = it
                            view.pause()
                            // TODO check that the scanned text is valid...
                            nc.navigate(Screen.Send, it)
                        }
                    }
                })
                view.resume()
            }
        }
    }
}
