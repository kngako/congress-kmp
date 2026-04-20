package ac.aux.compose.ui.view.model

import ac.aux.compose.database.model.SynchronizeNostrEventRequest
import ac.aux.compose.database.model.types.SynchronizationFilter
import ac.aux.compose.nostr.Relays
import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.ui.view.state.FeedListUIState
import ac.aux.compose.ui.view.state.SearchResultListUIState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayFeedsListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class MetadataEventDetailType {
    Posts, Replies, Articles, Followers, Following, Zaps, Photos, Videos,  Bookmarks, Reports, Relays
}

class MetadataEventDetailViewModel(
    initialMetadataEventDetailType: MetadataEventDetailType,
    val publicKey: HexKey,
    val nostrRepository: NostrRepository,
    val pagerState: PagerState
): ViewModel() {
    val logger = Logger.withTag(TAG)


    var feedJob: Job? = null

    var metadataEventDetailType: MetadataEventDetailType by mutableStateOf(initialMetadataEventDetailType)
        private set

    var feedListUIState: FeedListUIState by mutableStateOf(FeedListUIState.Loading)
        private set

    fun updateMetadataEventDetailType (metadataEventDetailType: MetadataEventDetailType, navigateToPage: () -> Unit) {
        this.metadataEventDetailType = metadataEventDetailType

        initiateMetadataFeed(this.metadataEventDetailType)
        navigateToPage.invoke()
    }

    fun initiateMetadataFeed(metadataEventDetailType: MetadataEventDetailType = this.metadataEventDetailType) {
        feedListUIState = FeedListUIState.Loading

        logger.d("initiateMetadataFeed")
        val synchronizationFilter = when (metadataEventDetailType) {
            MetadataEventDetailType.Posts -> {
                SynchronizationFilter(
                    authors = arrayOf(
                        publicKey
                    ),
                    kinds = arrayOf(
                        TextNoteEvent.KIND,
                        RepostEvent.KIND,
                    ),
                    limit = 50
                )
            }
            MetadataEventDetailType.Replies -> {
                SynchronizationFilter(
                    authors = arrayOf(
                        publicKey
                    ),
                    kinds = arrayOf(
                        TextNoteEvent.KIND,
                        ReactionEvent.KIND,
                    ),
                    // TODO: replyTo is set...
                    limit = 50
                )
            }
            MetadataEventDetailType.Articles -> {
                SynchronizationFilter(
                    authors = arrayOf(
                        publicKey
                    ),
                    kinds = arrayOf(
                        LongTextNoteEvent.KIND,
                    ),
                    limit = 50
                )
            }
            MetadataEventDetailType.Followers -> {
                SynchronizationFilter(
                    authors = arrayOf(
                        publicKey
                    ),
                    kinds = arrayOf(
                        ContactListEvent.KIND,
                    ),
                    // TODO: Check users with hexKey
                    limit = 50
                )
            }
            MetadataEventDetailType.Following -> {
                SynchronizationFilter(
                    authors = arrayOf(
                        publicKey
                    ),
                    kinds = arrayOf(
                        ContactListEvent.KIND,
                    ),
                    limit = 50
                )
            }
            MetadataEventDetailType.Zaps -> {
                SynchronizationFilter(
                    authors = arrayOf(
                        publicKey
                    ),
                    kinds = arrayOf(
                        LnZapEvent.KIND,
                    ),
                    // TODO: Zaps user has received...
                    limit = 50
                )
            }
            MetadataEventDetailType.Photos -> {
                SynchronizationFilter(
                    authors = arrayOf(
                        publicKey
                    ),
                    kinds = arrayOf(
                        TextNoteEvent.KIND,
                    ),
                    // TODO: filter for tags of pictures...
                    limit = 50
                )
            }
            MetadataEventDetailType.Videos -> {
                SynchronizationFilter(
                    authors = arrayOf(
                        publicKey
                    ),
                    kinds = arrayOf(
                        TextNoteEvent.KIND,
                    ),
                    // TODO: filter for tags of videos...
                    limit = 50
                )
            }
            MetadataEventDetailType.Bookmarks -> {
                SynchronizationFilter(
                    authors = arrayOf(
                        publicKey
                    ),
                    kinds = arrayOf(
                        BookmarkListEvent.KIND,
                    ),
                    limit = 50
                )
            }
            MetadataEventDetailType.Reports -> {
                SynchronizationFilter(
                    authors = arrayOf(
                        publicKey
                    ),
                    kinds = arrayOf(
                        ReportEvent.KIND,
                    ),
                    limit = 50
                )
            }
            MetadataEventDetailType.Relays -> {
                SynchronizationFilter(
                    authors = arrayOf(
                        publicKey
                    ),
                    kinds = arrayOf(
                        RelayFeedsListEvent.KIND,
                    ),
                    limit = 50
                )
            }
        }

        syncFeed(synchronizationFilter)
        loadFeed(synchronizationFilter)
    }



    private fun syncFeed(synchronizationFilter: SynchronizationFilter) {
        logger.d("syncFeed")

        viewModelScope.launch(Dispatchers.IO) {
            // Sync Notifications... might want to also run this in the background
            nostrRepository.queueSynchronizeNostrEvent(
                Relays.eventFinderRelaySet.map { normalizedRelay ->
                    SynchronizeNostrEventRequest(
                        purpose = "search",
                        synchronizationFilters = arrayOf(
                            synchronizationFilter
                        ),
                        relayURL = normalizedRelay.url
                    )
                }
            )
        }
    }

    private fun loadFeed(synchronizationFilter: SynchronizationFilter) {
        logger.d("loadFeed")

        feedJob?.cancel()
        feedJob = viewModelScope.launch(Dispatchers.IO) {
            // This is something not taking into consideration the results we are actually looking for...
            nostrRepository.observeNostrFeed(synchronizationFilter).collect { localNostrEvents ->
                feedListUIState = FeedListUIState.Loaded(
                    localNostrEvents = localNostrEvents
                )
            }
        }
    }

    companion object {
        const val TAG = "SearchViewModel"

        fun factory(
            initialMetadataEventDetailType: MetadataEventDetailType = MetadataEventDetailType.Posts,
            publicKey: HexKey,
            nostrRepository: NostrRepository,
            pagerState: PagerState
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MetadataEventDetailViewModel(
                    initialMetadataEventDetailType = initialMetadataEventDetailType,
                    nostrRepository = nostrRepository,
                    pagerState = pagerState,
                    publicKey = publicKey
                )
            }
        }
    }
}