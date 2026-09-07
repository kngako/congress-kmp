package press.mantra.compose.ui.composable.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import mantra.composeapp.generated.resources.Res
import mantra.composeapp.generated.resources.something_went_wrong
import mantra.composeapp.generated.resources.try_again
import org.jetbrains.compose.resources.stringResource
import press.mantra.compose.ui.theme.spacing

/**
 * The two states every list and every screen has, and neither of which had a home.
 *
 * Before this file the tree held **16 copies** of
 *
 *     Column(horizontalAlignment = CenterHorizontally) {
 *         Spacer(Modifier.height(48.dp))
 *         Text("Something went wrong")
 *     }
 *
 * and five of the same shape saying "No events were found". Not one of the sixteen offered
 * a retry, so every failure in this app was a dead end: the message named no cause, and
 * the only way out was the back button.
 *
 * M3's content guidance asks the opposite -- "emphasize the results of the user's
 * potential action", "tell users what will happen ... and how they can undo it" -- and its
 * structure guidance asks that a screen say what to do next rather than only what
 * happened.
 *
 * These two composables are deliberately plain: an icon, a line, and for [ErrorState] an
 * action when the caller can offer one. They are the thing 21 call sites collapse into,
 * not a design in their own right.
 */
@Composable
fun ErrorState(
    modifier: Modifier = Modifier,
    message: String = stringResource(Res.string.something_went_wrong),
    /**
     * `null` where the caller genuinely has nothing to retry -- a screen whose state came
     * from a navigation argument that was already wrong. Passing null is a decision;
     * omitting a retry that exists is the thing this parameter is here to make visible.
     */
    onRetry: (() -> Unit)? = null,
) {
    StateMessage(
        modifier = modifier,
        icon = Icons.Default.ErrorOutline,
        message = message,
        // The icon is decorative: the message beside it says what happened, and naming
        // this one would have a screen reader announce the trouble twice.
        iconDescription = Decorative,
        action = onRetry?.let {
            { TextButton(onClick = it) { Text(stringResource(Res.string.try_again)) } }
        },
    )
}

/**
 * A list with nothing in it yet.
 *
 * The message is required rather than defaulted, because "No events were found" told the
 * user nothing about which list was empty or what would fill it, and a shared default
 * would preserve exactly that. Say what is missing and, where there is one, how to get
 * some.
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Inbox,
    action: (@Composable () -> Unit)? = null,
) {
    StateMessage(
        modifier = modifier,
        icon = icon,
        message = message,
        iconDescription = Decorative,
        action = action,
    )
}

@Composable
private fun StateMessage(
    modifier: Modifier,
    icon: ImageVector,
    message: String,
    iconDescription: String?,
    action: (@Composable () -> Unit)?,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.spacing.screenMargin,
                vertical = MaterialTheme.spacing.emphasisGap,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.itemGap),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}
