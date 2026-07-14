package press.mantra.compose

import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation.compose.rememberNavController
import fr.acinq.phoenix.PhoenixGlobal

fun MainViewController() = ComposeUIViewController {
    val navController = rememberNavController()

    MantraApp(
        navController = navController,
        mantraGlobal = MantraGlobal(
            platformContext = PlatformContext()
        ),
        phoenixGlobal = PhoenixGlobal(
            ctx = fr.acinq.phoenix.utils.PlatformContext()
        )
    )
}