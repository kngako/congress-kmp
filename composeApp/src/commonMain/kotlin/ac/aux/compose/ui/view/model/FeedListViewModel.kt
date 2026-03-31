package ac.aux.compose.ui.view.model

import ac.aux.compose.managers.SeedManager
import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.ui.view.state.FeedListUIState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class FeedListViewModel(
    initialFeedListUIState: FeedListUIState,
    val nostrRepository: NostrRepository
): ViewModel() {
    var feedListUIState: FeedListUIState by mutableStateOf(initialFeedListUIState)
        private set

    fun retryLoad() {
        feedListUIState = FeedListUIState.Loading
        loadNostrFeed()
    }

    fun loadNostrFeed() {
        viewModelScope.launch(Dispatchers.IO) {
            Filter(
                kinds = listOf(
                    TextNoteEvent.KIND,
                    RepostEvent.KIND,
                    ReactionEvent.KIND,
                    LnZapEvent.KIND,
                ),
                tags = mapOf(
                    Pair("#p", listOf(SeedManager.activePublicKey().toHexKey()))
                )
            )
            val localProfile = nostrRepository.observeProfile(SeedManager.activePublicKey().toHexKey()).firstOrNull()

            if (localProfile?.profile == null) {
                feedListUIState = FeedListUIState.Error
            } else {
                val nostrFeed = nostrRepository.loadNostrFeed()

                feedListUIState = FeedListUIState.Loaded(
                    nostrEvents = nostrFeed
                )
            }
        }
    }

    companion object {
        const val TAG = "FeedListViewModel"

        fun factory(
            initialFeedListUIState: FeedListUIState = FeedListUIState.Loading,
            nostrRepository: NostrRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FeedListViewModel(
                    initialFeedListUIState = initialFeedListUIState,
                    nostrRepository = nostrRepository
                )
            }
        }
    }
}