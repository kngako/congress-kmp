package ac.cord.auxiliary.compose

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.navigation.compose.rememberNavController

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Aux",
    ) {
        val navController = rememberNavController()
        AuxApp(
            navController = navController,
            auxGlobal = AuxGlobal(
                platformContext = PlatformContext()
            )
        )
    }
}