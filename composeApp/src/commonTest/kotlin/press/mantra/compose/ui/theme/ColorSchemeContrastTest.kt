package press.mantra.compose.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every colour pair the six declared schemes promise, measured.
 *
 * M3's accessibility foundation gives two thresholds: small text needs 4.5:1 against
 * its background, and large text or a meaningful non-text boundary needs 3:1. Disabled
 * states are exempt. See docs/material-design-conformance.md, "The numbers".
 *
 * This passes as written -- the generated palette is sound, and the tightest pair in
 * the tree is `onPrimaryContainer` on `primaryContainer` at 4.61:1 light. It exists so
 * that a later edit to Color.kt or to the wiring in Theme.kt cannot quietly break one,
 * because a broken pair is invisible in review: the two hex values look unrelated, and
 * the failure only appears on a device, in one theme, to someone who cannot read it.
 *
 * It walks the real `ColorScheme` objects rather than rebuilding them from Color.kt, so
 * it also covers the wiring. A role pointed at its neighbour's value -- `surfaceContainerHigh
 * = surfaceContainerHighestLight` -- is a plausible slip that reads fine in isolation.
 *
 * What is deliberately *not* asserted here:
 *
 *  - **Monotonicity across the contrast ladder.** The obvious invariant, that
 *    high-contrast beats medium beats default for every pair, is false and correctly
 *    so. In the light high-contrast scheme `surfaceContainerHighest` goes darker to
 *    separate it from `surface`, which lowers its ratio against `onSurface` (13.30 ->
 *    12.29) while raising the one that matters. Ten pairs move that way. The floor is
 *    the invariant; the ladder is not.
 *  - **`outlineVariant`.** It reads 1.61:1 against surface, which looks alarming and is
 *    not a defect: M3's own baseline sits in the same range, and outlineVariant is a
 *    decorative divider. `outline`, the meaningful-boundary role, is asserted at 3:1.
 *  - **Call-site pairings.** Seven of those are below threshold today, the worst at
 *    1.00:1. They belong to phase 3 of the conformance plan and get their own table
 *    there; asserting them now would mean checking in a red test.
 */
class ColorSchemeContrastTest {

    private val schemes: List<Pair<String, ColorScheme>> = listOf(
        "light" to lightScheme,
        "dark" to darkScheme,
        "light medium-contrast" to mediumContrastLightColorScheme,
        "dark medium-contrast" to mediumContrastDarkColorScheme,
        "light high-contrast" to highContrastLightColorScheme,
        "dark high-contrast" to highContrastDarkColorScheme,
    )

    /**
     * Container role paired with the content role M3 assigns to it. Text drawn on the
     * first is drawn in the second, so each of these is a small-text pairing.
     */
    private val textPairs: List<Triple<String, (ColorScheme) -> Color, (ColorScheme) -> Color>> =
        listOf(
            Triple("onPrimary on primary", { s: ColorScheme -> s.primary }, { s: ColorScheme -> s.onPrimary }),
            Triple("onPrimaryContainer on primaryContainer", { s: ColorScheme -> s.primaryContainer }, { s: ColorScheme -> s.onPrimaryContainer }),
            Triple("onSecondary on secondary", { s: ColorScheme -> s.secondary }, { s: ColorScheme -> s.onSecondary }),
            Triple("onSecondaryContainer on secondaryContainer", { s: ColorScheme -> s.secondaryContainer }, { s: ColorScheme -> s.onSecondaryContainer }),
            Triple("onTertiary on tertiary", { s: ColorScheme -> s.tertiary }, { s: ColorScheme -> s.onTertiary }),
            Triple("onTertiaryContainer on tertiaryContainer", { s: ColorScheme -> s.tertiaryContainer }, { s: ColorScheme -> s.onTertiaryContainer }),
            Triple("onError on error", { s: ColorScheme -> s.error }, { s: ColorScheme -> s.onError }),
            Triple("onErrorContainer on errorContainer", { s: ColorScheme -> s.errorContainer }, { s: ColorScheme -> s.onErrorContainer }),
            Triple("onBackground on background", { s: ColorScheme -> s.background }, { s: ColorScheme -> s.onBackground }),
            Triple("onSurface on surface", { s: ColorScheme -> s.surface }, { s: ColorScheme -> s.onSurface }),
            Triple("onSurfaceVariant on surfaceVariant", { s: ColorScheme -> s.surfaceVariant }, { s: ColorScheme -> s.onSurfaceVariant }),
            Triple("inverseOnSurface on inverseSurface", { s: ColorScheme -> s.inverseSurface }, { s: ColorScheme -> s.inverseOnSurface }),
        )

    /**
     * The tonal surfaces. All eight carry `onSurface` content -- there is no
     * `onSurfaceContainer` role -- so every one of them is a text background, and a
     * scheme that darkens one of them without checking is how this breaks.
     */
    private val tonalSurfaces: List<Pair<String, (ColorScheme) -> Color>> = listOf(
        "surfaceDim" to { s: ColorScheme -> s.surfaceDim },
        "surfaceBright" to { s: ColorScheme -> s.surfaceBright },
        "surfaceContainerLowest" to { s: ColorScheme -> s.surfaceContainerLowest },
        "surfaceContainerLow" to { s: ColorScheme -> s.surfaceContainerLow },
        "surfaceContainer" to { s: ColorScheme -> s.surfaceContainer },
        "surfaceContainerHigh" to { s: ColorScheme -> s.surfaceContainerHigh },
        "surfaceContainerHighest" to { s: ColorScheme -> s.surfaceContainerHighest },
    )

    @Test
    fun `every content role reads at 4_5 to 1 on its container`() {
        val failures = mutableListOf<String>()

        schemes.forEach { (schemeName, scheme) ->
            textPairs.forEach { (pairName, container, content) ->
                val ratio = contrastRatio(container(scheme), content(scheme))
                if (ratio < SMALL_TEXT_MINIMUM) {
                    failures += "$schemeName: $pairName is ${ratio.format()}:1"
                }
            }
        }

        assertTrue(failures.isEmpty(), "below 4.5:1 —\n" + failures.joinToString("\n"))
    }

    @Test
    fun `onSurface reads at 4_5 to 1 on every tonal surface`() {
        val failures = mutableListOf<String>()

        schemes.forEach { (schemeName, scheme) ->
            tonalSurfaces.forEach { (surfaceName, surface) ->
                val ratio = contrastRatio(surface(scheme), scheme.onSurface)
                if (ratio < SMALL_TEXT_MINIMUM) {
                    failures += "$schemeName: onSurface on $surfaceName is ${ratio.format()}:1"
                }
            }
        }

        assertTrue(failures.isEmpty(), "below 4.5:1 —\n" + failures.joinToString("\n"))
    }

    @Test
    fun `outline separates from every surface it is drawn on at 3 to 1`() {
        val failures = mutableListOf<String>()

        schemes.forEach { (schemeName, scheme) ->
            (listOf("surface" to { s: ColorScheme -> s.surface }) + tonalSurfaces)
                .forEach { (surfaceName, surface) ->
                    val ratio = contrastRatio(surface(scheme), scheme.outline)
                    if (ratio < NON_TEXT_MINIMUM) {
                        failures += "$schemeName: outline on $surfaceName is ${ratio.format()}:1"
                    }
                }
        }

        assertTrue(failures.isEmpty(), "below 3:1 —\n" + failures.joinToString("\n"))
    }

    @Test
    fun `a filled container stands off the surface behind it at 3 to 1`() {
        // M3 asks for 3:1 between a clustered interactive container and its background.
        // A standalone element such as a FAB is exempt by prominence, but `primary` and
        // `error` are both used for buttons that sit beside other buttons.
        val failures = mutableListOf<String>()

        schemes.forEach { (schemeName, scheme) ->
            listOf(
                "primary" to scheme.primary,
                "error" to scheme.error,
            ).forEach { (roleName, role) ->
                val ratio = contrastRatio(scheme.surface, role)
                if (ratio < NON_TEXT_MINIMUM) {
                    failures += "$schemeName: $roleName on surface is ${ratio.format()}:1"
                }
            }
        }

        assertTrue(failures.isEmpty(), "below 3:1 —\n" + failures.joinToString("\n"))
    }

    private companion object {
        /** WCAG 2.x, small text. */
        const val SMALL_TEXT_MINIMUM = 4.5

        /** WCAG 2.x, large text and meaningful non-text elements. */
        const val NON_TEXT_MINIMUM = 3.0
    }
}

/**
 * WCAG relative luminance of one sRGB channel.
 *
 * The 0.03928 knee and the 2.4 exponent are the specification's, not an approximation
 * of gamma 2.2 -- swapping in the simpler curve moves borderline pairs by enough to
 * change a verdict, which is the whole point of this file.
 */
private fun channelLuminance(component: Float): Double {
    val c = component.toDouble()
    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

private fun Color.relativeLuminance(): Double =
    0.2126 * channelLuminance(red) +
        0.7152 * channelLuminance(green) +
        0.0722 * channelLuminance(blue)

/**
 * Contrast ratio between two opaque colours, 1.0 to 21.0.
 *
 * Both arguments must be opaque. A translucent colour has no ratio of its own -- it has
 * one only once composited over something -- so composite it first and pass the result.
 * Phase 3 needs that for the `.copy(alpha = 0.5f)` call sites; the schemes here are all
 * fully opaque.
 */
internal fun contrastRatio(a: Color, b: Color): Double {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** Two decimal places, without pulling in a platform formatter. */
private fun Double.format(): String {
    val scaled = (this * 100).toInt()
    return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
}
