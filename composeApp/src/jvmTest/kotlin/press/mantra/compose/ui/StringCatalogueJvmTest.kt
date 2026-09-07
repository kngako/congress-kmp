package press.mantra.compose.ui

import kotlinx.coroutines.runBlocking
import mantra.composeapp.generated.resources.Res
import mantra.composeapp.generated.resources.currently_no_messages_have_been_shared
import mantra.composeapp.generated.resources.don_t_sign
import mantra.composeapp.generated.resources.something_went_wrong
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The string catalogue reads back what the source used to say.
 *
 * Moving 315 literals out of composables and into `strings.xml` is a rename that a
 * compiler cannot check: every call site still compiles if a resource holds the wrong
 * text, and the mistake surfaces as wrong words on a screen nobody has opened.
 *
 * The escapes are the part worth a test rather than an assumption. A string going into an
 * XML resource needs `'` escaped as `\'` and `%` doubled, and the generated `.cvr` stores
 * the escaped form verbatim -- base64 of `We couldn\'t find...`. Whether the backslash is
 * still there when `stringResource` hands the text to a `Text` is a property of the
 * resources library, not of the extraction, and the failure mode is an app that literally
 * displays `Don\'t sign`.
 *
 * `getString` is the non-composable reader for the same resources, so this can assert it
 * without a composition.
 */
class StringCatalogueJvmTest {

    @Test
    fun `an apostrophe survives the round trip through the catalogue`() = runBlocking {
        assertEquals("Don't sign", getString(Res.string.don_t_sign))
    }

    @Test
    fun `an escaped newline comes back as a newline`() = runBlocking {
        assertEquals(
            "Currently no messages have been shared.\nBreak the ice.",
            getString(Res.string.currently_no_messages_have_been_shared),
        )
    }

    @Test
    fun `a plain string comes back unchanged`() = runBlocking {
        assertEquals("Something went wrong", getString(Res.string.something_went_wrong))
    }
}
