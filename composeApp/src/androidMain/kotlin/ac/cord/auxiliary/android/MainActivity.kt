package ac.cord.auxiliary.android

import ac.cord.auxiliary.compose.AuxApp
import ac.cord.auxiliary.compose.AuxGlobal
import ac.cord.auxiliary.compose.PlatformContext
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
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

            AuxApp(
                navController = navController,
                auxGlobal = AuxGlobal(
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
        if (NfcStateRepository.isReading()) {
            NfcStateRepository.updateState(NfcState.Inactive)
        }
        nfcAdapter?.disableReaderMode(this@MainActivity)
    }

}