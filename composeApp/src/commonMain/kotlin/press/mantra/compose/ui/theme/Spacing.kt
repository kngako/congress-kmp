package press.mantra.compose.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * M3's spacing scale, and the semantic names layered over it.
 *
 * `MaterialTheme` has no spacing slot, so this rides a composition local beside it, the
 * same way [ExtendedColors] does. [TorchTheme] provides it.
 *
 * **The scale** is M3's own, an 8dp system where `space100 = 8dp`, including the sub-8
 * nested units Material defines because its components need them. Numbers are from
 * m3.material.io/m3/pages/spacing/tokens, read September 2026; see
 * docs/material-design-conformance.md for the full table.
 *
 * **Why a `data class` and not constants.** Nothing scales today, but two things are
 * coming that need to: spacing adapts across breakpoints, and M3 has a density setting
 * for data-heavy views. Both are a matter of providing a different [Spacing] instance
 * rather than of touching a call site, and that is only true if the values arrive through
 * the local. A file of top-level `val`s would read the same and adapt to nothing.
 */
@Immutable
data class Spacing(
    val space0: Dp = 0.dp,
    val space25: Dp = 2.dp,
    val space50: Dp = 4.dp,
    val space75: Dp = 6.dp,
    val space100: Dp = 8.dp,
    val space125: Dp = 10.dp,
    val space150: Dp = 12.dp,
    val space175: Dp = 14.dp,
    val space200: Dp = 16.dp,
    val space250: Dp = 20.dp,
    val space300: Dp = 24.dp,
    val space400: Dp = 32.dp,
    val space450: Dp = 36.dp,
    val space500: Dp = 40.dp,
    val space600: Dp = 48.dp,
    val space700: Dp = 56.dp,
    val space800: Dp = 64.dp,
    val space900: Dp = 72.dp,
) {
    // -----------------------------------------------------------------------
    // Semantic names
    // -----------------------------------------------------------------------
    //
    // `space125` at a call site is no more readable than `10.dp` -- it says the size and
    // not the job. These say the job, and they are what call sites should reach for; the
    // raw scale is for the cases none of them fits.
    //
    // The distinction M3 draws, and the reason the names are split this way:
    //
    //   padding  space inside an element, between its edge and its content
    //   gap      space between elements in a row, column or grid
    //   margin   space outside an element, between it and its parent or the screen
    //
    // The spec is explicit that margins are a last resort -- "define padding and gaps on
    // the parent container", "avoid defining margins on child elements as they usually
    // aren't uniform, and require more tokens" -- so there is exactly one margin here,
    // for the screen edge, and everything else is padding or a gap.

    /** Screen edge to content. The one margin; everything inside a screen is padding or a gap. */
    val screenMargin: Dp get() = space200

    /** Inside a card, dialog, sheet or list row: container edge to its content. */
    val containerPadding: Dp get() = space200

    /** Inside a compact container -- a chip, a badge, a dense row. */
    val compactPadding: Dp get() = space100

    /** Between two elements that belong to the same thought: a label and its value. */
    val relatedGap: Dp get() = space50

    /** The default gap between items in a list or column. */
    val itemGap: Dp get() = space100

    /** Between one group of content and the next within a screen. */
    val sectionGap: Dp get() = space300

    /** Around a lone element that needs to stand apart -- an empty state, a hero action. */
    val emphasisGap: Dp get() = space500

    /** Between adjacent touch targets, which M3 asks to be at least 8dp apart. */
    val targetGap: Dp get() = space100
}

val LocalSpacing = staticCompositionLocalOf { Spacing() }

/**
 * `MaterialTheme.spacing.containerPadding`, to match `MaterialTheme.colorScheme.primary`.
 *
 * The alternative is `LocalSpacing.current`, which has to be read into a local before it
 * can be used and so cannot appear inline in a `Modifier` chain. That difference decides
 * whether a call site reads
 *
 *     Modifier.padding(MaterialTheme.spacing.containerPadding)
 *
 * or acquires a `val spacing = LocalSpacing.current` several lines above, in whichever
 * composable happens to enclose it. Over 500 call sites the second shape is what stops
 * people using the scale.
 */
val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current
