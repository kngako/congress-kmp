package press.mantra.compose.ui.composable.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * Gives [content] a `ViewModelStore` of its own, cleared whenever [scopeKey] changes.
 *
 * **`key(...)` does not do this, which is the whole reason this exists.** A composition
 * key replaces a group -- `remember`s are forgotten, `LaunchedEffect`s restart -- but
 * `viewModel()` is not composition state. It resolves against
 * `LocalViewModelStoreOwner.current` under a key derived from the *class*, so two
 * compositions of one screen under one owner get the same instance no matter how they are
 * keyed, and the factory is never invoked for the second. Being inside `key(someId)` buys
 * nothing: the store is the navigation entry's, and it outlives every group in it.
 *
 * That is only a problem where one screen is composed for a succession of subjects without
 * the navigation entry changing, which in this app means a list-detail pane: the home
 * screen stays on `HomeRoute` while the detail pane is asked for one chat room after
 * another. A pushed route has no such problem, because each `NavBackStackEntry` is already
 * a store of its own -- which is why this only wraps the pane.
 *
 * The store is cleared on the way out rather than left to the parent, so the room just
 * left does not keep its relay subscriptions and database collectors running. That is the
 * other half of the job: keying the `viewModel()` calls by room id instead would fix what
 * is on screen and quietly accumulate a live view model per room visited.
 *
 * Nothing in this app puts a `SavedStateHandle` in a view model, so a bare
 * `ViewModelStoreOwner` is the whole contract a scope needs here; it deliberately does not
 * implement `HasDefaultViewModelProviderFactory`, because every call inside passes its own
 * factory.
 */
@Composable
fun ViewModelStoreScopedTo(scopeKey: Any?, content: @Composable () -> Unit) {
    val owner = remember(scopeKey) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }

    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
