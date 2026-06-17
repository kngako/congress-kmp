package at.torch.compose.ui.view.model

import at.torch.compose.database.model.NegentropySynchronizeRequest
import at.torch.compose.database.model.types.SynchronizationFilter
import at.torch.compose.ui.composable.navigation.routes.NostrEventDetailRoute
import at.torch.compose.ui.composable.widgets.feed.ListView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Instant

class InReplyToViewModel(
    val nostrEvent: at.torch.compose.database.model.NostrEvent,
    val profile: at.torch.compose.database.model.Profile?,
    initialFeedListUIState: at.torch.compose.ui.view.state.FeedListUIState,
    val synchronizationFilter: at.torch.compose.database.model.types.SynchronizationFilter,
    val nostrRepository: at.torch.compose.repository.NostrRepository
): ViewModel() {
    val logger = Logger.withTag(TAG)
    var inReplyToFeedListUIState: at.torch.compose.ui.view.state.FeedListUIState by mutableStateOf(initialFeedListUIState)
        private set

    val isSynchronizationPending: MutableState<Boolean> = mutableStateOf(false)

    fun initiate() {
        logger.d("init")
        scheduleSynchronization()
        observeLocalNostrFeed()
        observeSynchronizeNostrEventRequestByPurposeAndStatusCount()
    }

    fun observeLocalNostrFeed() {
        logger.d("observeLocalNostrFeed")
        viewModelScope.launch(Dispatchers.IO) {
            nostrRepository.observeNostrFeed(
                synchronizationFilter
            ).distinctUntilChanged().collect { localNostrEvents ->
                inReplyToFeedListUIState = _root_ide_package_.at.torch.compose.ui.view.state.FeedListUIState.Loaded(
                    localNostrEvents = localNostrEvents
                )
            }
        }
    }

    fun scheduleSynchronization() {
        logger.d("scheduleSynchronization")
        viewModelScope.launch(Dispatchers.IO) {
            // Sync Notifications... might want to also run this in the background
            val publicRelays = profile?.profilePublicRelays(
                nostrRepository
            ) ?: _root_ide_package_.at.torch.compose.nostr.Relays.negentropicRelaySet

            nostrRepository.queueNegentropySynchronizeRequest(
                publicRelays.take(3).map { normalizedRelayUrl ->
                    _root_ide_package_.ac.cord.compose.database.model.NegentropySynchronizeRequest(
                        id = _root_ide_package_.at.torch.compose.database.model.NegentropySynchronizeRequest.Companion.computeId(
                            relayURL = normalizedRelayUrl.url,
                            synchronizationFilter = synchronizationFilter
                        ),
                        purpose = "inReplyTo",
                        synchronizationFilter = synchronizationFilter,
                        relayURL = normalizedRelayUrl.url,
                        level = 0
                    )
                }
            )
        }
    }

    fun observeSynchronizeNostrEventRequestByPurposeAndStatusCount() {
        logger.d("observeSynchronizeNostrEventRequestByPurposeAndStatusCount")
        viewModelScope.launch(Dispatchers.IO) {
            nostrRepository.observeSynchronizeNostrEventRequestByPurposeAndStatusCount("feed").distinctUntilChanged().collect { count ->
                logger.d("Pending/Processing Synchronization Request: $count")
                isSynchronizationPending.value = count > 0
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun RenderFeed(
        activeUserPublicKey: String,
        onNavigateToEvent: (at.torch.compose.ui.composable.navigation.routes.Route) -> Unit
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val feedListUIState = inReplyToFeedListUIState) {
                _root_ide_package_.at.torch.compose.ui.view.state.FeedListUIState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(
                            modifier = Modifier.height(50.dp)
                        )
                        Text(
                            text = "Something went wrong",
                        )
                    }
                }
                is at.torch.compose.ui.view.state.FeedListUIState.Loaded -> {
                    if (feedListUIState.localNostrEvents.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        )  {
                            Spacer(
                                modifier = Modifier.height(50.dp)
                            )
                            Text(
                                text = "No events were found",
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = feedListUIState.localNostrEvents,
                                key = { localNostrEvent -> localNostrEvent.nostrEvent.id}
                            ) { localNostrEvent ->
                                localNostrEvent.ListView(
                                    onNavigateToEvent = { nostrEventId ->
                                        onNavigateToEvent.invoke(
                                            _root_ide_package_.ac.cord.compose.ui.composable.navigation.routes.NostrEventDetailRoute(
                                                activeUserPublicKey = activeUserPublicKey,
                                                nostrEventId = nostrEventId
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }

                }
                _root_ide_package_.at.torch.compose.ui.view.state.FeedListUIState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    )  {
                        Spacer(
                            modifier = Modifier.height(50.dp)
                        )
                        Text(
                            text = "Loading",
                            textAlign = TextAlign.Center
                        )
                        Spacer(
                            modifier = Modifier.height(50.dp)
                        )

                        LoadingIndicator()
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "InReplyToViewModel"

        fun factory(
            nostrEvent: at.torch.compose.database.model.NostrEvent,
            profile: at.torch.compose.database.model.Profile?,
            createdAt: Instant,
            initialFeedListUIState: at.torch.compose.ui.view.state.FeedListUIState = _root_ide_package_.at.torch.compose.ui.view.state.FeedListUIState.Loading,
            synchronizationFilter: at.torch.compose.database.model.types.SynchronizationFilter = _root_ide_package_.ac.cord.compose.database.model.types.SynchronizationFilter(
                kinds = arrayOf(
                    TextNoteEvent.KIND,
                    ReactionEvent.KIND,
                    RepostEvent.KIND,
                ),
                tags = mapOf(
                    "e" to listOf(
                        nostrEvent.id
                    )
                ),
                since = createdAt
            ),
            nostrRepository: at.torch.compose.repository.NostrRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                InReplyToViewModel(
                    initialFeedListUIState = initialFeedListUIState,
                    nostrEvent = nostrEvent,
                    profile = profile,
                    synchronizationFilter = synchronizationFilter,
                    nostrRepository = nostrRepository
                )
            }
        }

    }
}