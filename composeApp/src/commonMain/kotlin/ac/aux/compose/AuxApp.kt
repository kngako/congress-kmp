package ac.aux.compose

import ac.aux.compose.ui.composable.navigation.AuxNavHost
import ac.aux.compose.ui.composable.navigation.routes.LandingRoute
import ac.aux.compose.ui.theme.AuxTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController

@Composable
fun AuxApp(
    navController: NavHostController,
) {
    AuxTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            AuxNavHost(

                navController = navController,
                startDestination = LandingRoute
            )

        }
    }
}