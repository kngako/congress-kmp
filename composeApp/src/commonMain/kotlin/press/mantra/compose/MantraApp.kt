package press.mantra.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import press.mantra.compose.ui.composable.navigation.MantraNavHost
import press.mantra.compose.ui.composable.widgets.ProvideSnackbarHost
import press.mantra.compose.ui.theme.MantraTheme
import fr.acinq.phoenix.PhoenixGlobal

@Composable
fun MantraApp(
    navController: NavHostController,
    mantraGlobal: MantraGlobal,
    phoenixGlobal: PhoenixGlobal
) {
    MantraTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            // One host state for the whole app rather than one per screen. Only one
            // Scaffold is composed at a time under a NavHost, so the message renders in
            // whichever screen is on top -- and a view model coroutine reporting an
            // outcome does not have to be handed a state through the parameter list of
            // every composable between it and the Scaffold.
            ProvideSnackbarHost {
                MantraNavHost(
                    mantraGlobal = mantraGlobal,
                    phoenixGlobal = phoenixGlobal,
                    navController = navController
                )
            }
        }
    }
}