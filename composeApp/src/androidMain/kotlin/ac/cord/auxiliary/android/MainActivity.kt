package ac.cord.auxiliary.android

import ac.cord.auxiliary.compose.AuxApp
import ac.cord.auxiliary.compose.AuxGlobal
import ac.cord.auxiliary.compose.PlatformContext
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
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
                )
            )
        }
    }
}