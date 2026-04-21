package ac.cord.auxiliary.compose.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
actual fun themeColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    darkScheme: ColorScheme,
    lightScheme: ColorScheme
): ColorScheme {
    return when {
        darkTheme -> darkScheme
        else -> lightScheme
    }
}