package at.torch.compose

import at.torch.compose.ui.composable.navigation.TorchNavHost
import at.torch.compose.ui.theme.TorchTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import fr.acinq.phoenix.PhoenixGlobal

@Composable
fun TorchApp(
    navController: NavHostController,
    torchGlobal: TorchGlobal,
    phoenixGlobal: PhoenixGlobal
) {
    TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            TorchNavHost(
                torchGlobal = torchGlobal,
                phoenixGlobal = phoenixGlobal,
                navController = navController
            )
        }
    }
}