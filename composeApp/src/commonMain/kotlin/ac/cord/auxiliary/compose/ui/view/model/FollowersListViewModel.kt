package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.database.model.SynchronizeNostrEventRequest
import ac.cord.auxiliary.compose.database.model.types.SynchronizationFilter
import ac.cord.auxiliary.compose.nostr.Relays
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.NostrEventDetailRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.Route
import ac.cord.auxiliary.compose.ui.view.state.FollowersListUIState
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class FollowersListViewModel(
    initialFollowingListUIState: FollowersListUIState,
    val publicKey: String,
    val synchronizationFilter: SynchronizationFilter,
    val nostrRepository: NostrRepository
): ViewModel() {
    val logger = Logger.withTag(TAG)
    var followersListUIState: FollowersListUIState by mutableStateOf(initialFollowingListUIState)
        private set

    val isSynchronizationPending: MutableState<Boolean> = mutableStateOf(false)

    fun initiate() {
        logger.d("initiate")
        scheduleSynchronization()
        observeLocalNostrFeed()
        observeSynchronizeNostrEventRequestByPurposeAndStatusCount()
    }

    fun observeLocalNostrFeed() {
        logger.d("observeLocalNostrFeed")
        viewModelScope.launch(Dispatchers.IO) {
            nostrRepository.observeProfileFollowers(
                publicKey
            ).collect { localFollowers ->
                followersListUIState = FollowersListUIState.Loaded(
                    followers = localFollowers?.followers ?: emptyList()
                )
            }
        }
    }

    fun scheduleSynchronization() {
        logger.d("scheduleSynchronization")
        viewModelScope.launch(Dispatchers.IO) {
            // Sync Notifications... might want to also run this in the background
            nostrRepository.queueSynchronizeNostrEvent(
                Relays.eventPublishRelaySet.take(1).map { normalizedRelay -> // TODO: Sync from all the publish relays...
                    SynchronizeNostrEventRequest(
                        purpose = "feed",
                        synchronizationFilters = arrayOf(
                            synchronizationFilter
                        ),
                        relayURL = normalizedRelay.url,
                        level = 0
                    )
                }
            )
        }
    }

    fun observeSynchronizeNostrEventRequestByPurposeAndStatusCount() {
        logger.d("observeSynchronizeNostrEventRequestByPurposeAndStatusCount")
        viewModelScope.launch(Dispatchers.IO) {
            nostrRepository.observeSynchronizeNostrEventRequestByPurposeAndStatusCount("feed").collect { count ->
                logger.d("Pending/Processing Synchronization Request: $count")
                isSynchronizationPending.value = count > 0
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun RenderFeed(
        onNavigateToEvent: (Route) -> Unit
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val followersListUIState = followersListUIState) {
                FollowersListUIState.Error -> {
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
                is FollowersListUIState.Loaded -> {
                    if (followersListUIState.followers.isEmpty()) {
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
                                items = followersListUIState.followers,
                                key = { profile -> profile.publicKey }
                            ) { follower ->
                                Column(
                                    modifier = Modifier.clickable {
                                        onNavigateToEvent.invoke(
                                            NostrEventDetailRoute(
                                                follower.nostrEventId
                                            )
                                        )
                                    }
                                ) {
                                    Text(
                                        text = follower.displayName ?: follower.userName ?: follower.userName ?: follower.publicKey
                                    )

                                    follower.nip05?.let {
                                        Text(
                                            text = follower.nip05
                                        )
                                    }
                                }
                            }
                        }
                    }

                }
                FollowersListUIState.Loading -> {
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
        const val TAG = "FeedListViewModel"

        fun factory(
            initialFeedListUIState: FollowersListUIState = FollowersListUIState.Loading,
            publicKey: String,
            synchronizationFilter: SynchronizationFilter,
            nostrRepository: NostrRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FollowersListViewModel(
                    initialFollowingListUIState = initialFeedListUIState,
                    publicKey = publicKey,
                    synchronizationFilter = synchronizationFilter,
                    nostrRepository = nostrRepository
                )
            }
        }
    }
}