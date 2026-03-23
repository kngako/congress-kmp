package ac.aux.compose

import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation.compose.rememberNavController

fun MainViewController() = ComposeUIViewController {
    val navController = rememberNavController()

    AuxApp(navController)
}