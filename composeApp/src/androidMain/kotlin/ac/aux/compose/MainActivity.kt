package ac.aux.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.vitorpamplona.quartz.utils.platform

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
