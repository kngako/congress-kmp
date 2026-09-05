package press.mantra.compose.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * `dynamicColor` is ignored: it means Material You, which reads a wallpaper-derived palette
 * from the android system and has no desktop counterpart. The app's own schemes are used
 * whatever the caller asks for.
 */
@Composable
actual fun themeColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    darkScheme: ColorScheme,
    lightScheme: ColorScheme
): ColorScheme = if (darkTheme) darkScheme else lightScheme
