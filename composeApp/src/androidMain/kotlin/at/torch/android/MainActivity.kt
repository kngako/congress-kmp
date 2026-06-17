package at.torch.android

import at.torch.compose.PlatformContext
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import at.torch.compose.TorchApp
import at.torch.compose.TorchGlobal
import com.machankura.compose.ui.composable.widgets.nfc.NfcState
import com.machankura.compose.ui.composable.widgets.nfc.NfcStateRepository
import fr.acinq.phoenix.PhoenixGlobal
import fr.acinq.phoenix.android.services.HceService

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val navController = rememberNavController()

            TorchApp(
                navController = navController,
                torchGlobal = TorchGlobal(
                    platformContext = PlatformContext(
                        applicationContext = applicationContext
                    )
                ),
                phoenixGlobal = PhoenixGlobal(
                    ctx = fr.acinq.phoenix.utils.PlatformContext(
                        applicationContext = applicationContext
                    )
                )

            )
        }
    }

    fun stopHceService() {
        stopService(Intent(this@MainActivity, HceService::class.java))
    }

    fun stopNfcReader() {
        if (_root_ide_package_.com.machankura.compose.ui.composable.widgets.nfc.NfcStateRepository.isReading()) {
            _root_ide_package_.com.machankura.compose.ui.composable.widgets.nfc.NfcStateRepository.updateState(
                _root_ide_package_.com.machankura.compose.ui.composable.widgets.nfc.NfcState.Inactive)
        }
        nfcAdapter?.disableReaderMode(this@MainActivity)
    }

}