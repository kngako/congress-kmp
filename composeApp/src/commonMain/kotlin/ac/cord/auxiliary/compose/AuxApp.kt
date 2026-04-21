package ac.cord.auxiliary.compose

import ac.cord.auxiliary.compose.ui.composable.navigation.AuxNavHost
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController

@Composable
fun AuxApp(
    navController: NavHostController,
    auxGlobal: AuxGlobal
) {
    AuxTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            AuxNavHost(
                auxGlobal = auxGlobal,
                navController = navController
            )
        }
    }
}