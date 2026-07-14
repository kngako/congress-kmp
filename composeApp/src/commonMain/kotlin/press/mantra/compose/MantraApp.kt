package press.mantra.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import press.mantra.compose.ui.composable.navigation.MantraNavHost
import press.mantra.compose.ui.theme.TorchTheme
import fr.acinq.phoenix.PhoenixGlobal

@Composable
fun MantraApp(
    navController: NavHostController,
    mantraGlobal: MantraGlobal,
    phoenixGlobal: PhoenixGlobal
) {
    TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            MantraNavHost(
                mantraGlobal = mantraGlobal,
                phoenixGlobal = phoenixGlobal,
                navController = navController
            )
        }
    }
}