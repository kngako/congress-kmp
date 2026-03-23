package ac.aux.compose.ui.view.state

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.time.Clock

sealed interface FeedListUIState {
    data class Loaded(
        val events: List<Event>
    ): FeedListUIState

    data object Error: FeedListUIState
    data object Loading: FeedListUIState
}

