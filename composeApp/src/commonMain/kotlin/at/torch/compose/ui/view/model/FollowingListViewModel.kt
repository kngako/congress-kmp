package at.torch.compose.ui.view.model

import at.torch.compose.database.model.NegentropySynchronizeRequest
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class FollowingListViewModel(
    initialFollowingListUIState: at.torch.compose.ui.view.state.FollowingListUIState,
    val publicKey: String,
    val synchronizationFilter: at.torch.compose.database.model.types.SynchronizationFilter,
    val nostrRepository: at.torch.compose.repository.NostrRepository
): ViewModel() {
    val logger = Logger.withTag(TAG)
    var followingListUIState: at.torch.compose.ui.view.state.FollowingListUIState by mutableStateOf(initialFollowingListUIState)
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
            nostrRepository.observeProfileWithFollowing(
                publicKey
            ).collect { localFollowing ->
                followingListUIState = _root_ide_package_.at.torch.compose.ui.view.state.FollowingListUIState.Loaded(
                    following = localFollowing?.following ?: emptyList()
                )
            }
        }
    }

    fun scheduleSynchronization() {
        logger.d("scheduleSynchronization")
        viewModelScope.launch(Dispatchers.IO) {
            // Sync Notifications... might want to also run this in the background
            nostrRepository.queueNegentropySynchronizeRequest(
                _root_ide_package_.at.torch.compose.nostr.Relays.negentropicRelaySet.shuffled().map { normalizedRelayUrl ->
                    _root_ide_package_.at.torch.compose.database.model.NegentropySynchronizeRequest(
                        id = _root_ide_package_.at.torch.compose.database.model.NegentropySynchronizeRequest.Companion.computeId(
                            relayURL = normalizedRelayUrl.url,
                            synchronizationFilter = synchronizationFilter
                        ),
                        purpose = "following",
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
            when (val feedListUIState = followingListUIState) {
                _root_ide_package_.at.torch.compose.ui.view.state.FollowingListUIState.Error -> {
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
                is at.torch.compose.ui.view.state.FollowingListUIState.Loaded -> {
                    if (feedListUIState.following.isEmpty()) {
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
                                items = feedListUIState.following,
                                key = { profile -> profile.publicKey }
                            ) { profile ->
                                profile.RenderAsListItem(
                                    activeUserPublicKey = activeUserPublicKey,
                                    onNavigateToEvent = onNavigateToEvent
                                )
                            }
                        }
                    }

                }
                _root_ide_package_.at.torch.compose.ui.view.state.FollowingListUIState.Loading -> {
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
        const val TAG = "FollowingListViewModel"

        fun factory(
            initialFeedListUIState: at.torch.compose.ui.view.state.FollowingListUIState = _root_ide_package_.at.torch.compose.ui.view.state.FollowingListUIState.Loading,
            publicKey: String,
            synchronizationFilter: at.torch.compose.database.model.types.SynchronizationFilter,
            nostrRepository: at.torch.compose.repository.NostrRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FollowingListViewModel(
                    initialFollowingListUIState = initialFeedListUIState,
                    publicKey = publicKey,
                    synchronizationFilter = synchronizationFilter,
                    nostrRepository = nostrRepository
                )
            }
        }
    }
}