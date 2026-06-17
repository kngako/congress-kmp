package at.torch.compose

import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation.compose.rememberNavController
import fr.acinq.phoenix.PhoenixGlobal

fun MainViewController() = ComposeUIViewController {
    val navController = rememberNavController()

    TorchApp(
        navController = navController,
        torchGlobal = TorchGlobal(
            platformContext = PlatformContext()
        ),
        phoenixGlobal = PhoenixGlobal(
            ctx = fr.acinq.phoenix.utils.PlatformContext()
        )
    )
}