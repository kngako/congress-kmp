package press.mantra.compose.ui.view.model

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import press.mantra.compose.ui.theme.spacing
import mantra.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import mantra.composeapp.generated.resources.loading
import mantra.composeapp.generated.resources.no_events_were_found
import mantra.composeapp.generated.resources.something_went_wrong

class FollowersListViewModel(
    initialFollowingListUIState: press.mantra.compose.ui.view.state.FollowersListUIState,
    val publicKey: String,
    val synchronizationFilter: press.mantra.compose.database.model.types.SynchronizationFilter,
    val nostrRepository: press.mantra.compose.repository.NostrRepository
): ViewModel() {
    val logger = Logger.withTag(TAG)
    var followersListUIState: press.mantra.compose.ui.view.state.FollowersListUIState by mutableStateOf(initialFollowingListUIState)
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
            nostrRepository.observeProfileWithFollowers(
                publicKey
            ).collect { localFollowers ->
                followersListUIState = press.mantra.compose.ui.view.state.FollowersListUIState.Loaded(
                    followers = localFollowers?.followers ?: emptyList()
                )
            }
        }
    }

    fun scheduleSynchronization() {
        logger.d("scheduleSynchronization")
        viewModelScope.launch(Dispatchers.IO) {
            // Sync Notifications... might want to also run this in the background
            nostrRepository.queueNegentropySynchronizeRequest(
                press.mantra.compose.nostr.Relays.DefaultDMRelayList.shuffled().map { normalizedRelayUrl ->
                    press.mantra.compose.database.model.NegentropySynchronizeRequest(
                        id = press.mantra.compose.database.model.NegentropySynchronizeRequest.computeId(
                            relayURL = normalizedRelayUrl.url,
                            synchronizationFilter = synchronizationFilter
                        ),
                        purpose = "followers",
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
        onNavigateToEvent: (press.mantra.compose.ui.composable.navigation.routes.Route) -> Unit
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val followersListUIState = followersListUIState) {
                press.mantra.compose.ui.view.state.FollowersListUIState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(
                            modifier = Modifier.height(MaterialTheme.spacing.space600)
                        )
                        Text(
                            text = stringResource(Res.string.something_went_wrong),
                        )
                    }
                }
                is press.mantra.compose.ui.view.state.FollowersListUIState.Loaded -> {
                    if (followersListUIState.followers.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        )  {
                            Spacer(
                                modifier = Modifier.height(MaterialTheme.spacing.space600)
                            )
                            Text(
                                text = stringResource(Res.string.no_events_were_found),
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space125)
                        ) {
                            items(
                                items = followersListUIState.followers,
                                key = { profile -> profile.publicKey }
                            ) { follower ->
                                follower.RenderAsListItem(
                                    activeUserPublicKey = activeUserPublicKey,
                                    onNavigateToEvent = onNavigateToEvent
                                )
                            }
                        }
                    }

                }
                press.mantra.compose.ui.view.state.FollowersListUIState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    )  {
                        Spacer(
                            modifier = Modifier.height(MaterialTheme.spacing.space600)
                        )
                        Text(
                            text = stringResource(Res.string.loading),
                            textAlign = TextAlign.Center
                        )
                        Spacer(
                            modifier = Modifier.height(MaterialTheme.spacing.space600)
                        )

                        LoadingIndicator()
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "FollowersListViewModel"

        fun factory(
            initialFeedListUIState: press.mantra.compose.ui.view.state.FollowersListUIState = press.mantra.compose.ui.view.state.FollowersListUIState.Loading,
            publicKey: String,
            synchronizationFilter: press.mantra.compose.database.model.types.SynchronizationFilter,
            nostrRepository: press.mantra.compose.repository.NostrRepository,
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