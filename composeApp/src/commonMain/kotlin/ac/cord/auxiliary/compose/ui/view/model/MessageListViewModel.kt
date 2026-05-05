package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.database.model.NegentropySynchronizeRequest
import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfileWithFollowing
import ac.cord.auxiliary.compose.database.model.types.SynchronizationFilter
import ac.cord.auxiliary.compose.nostr.Relays
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.composable.HomeScreen
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.Route
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import ac.cord.auxiliary.compose.ui.view.state.FollowingListUIState
import ac.cord.auxiliary.compose.ui.view.state.HomeScreenUIState
import ac.cord.auxiliary.compose.ui.view.state.MessageListUIState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MessageListViewModel(
    initialMessageListUIState: MessageListUIState,
    val publicKey: String,
    val synchronizationFilter: SynchronizationFilter,
    val nostrRepository: NostrRepository
): ViewModel() {
    val logger = Logger.withTag(TAG)
    var messageListUIState: MessageListUIState by mutableStateOf(initialMessageListUIState)
        private set

    val isSynchronizationPending: MutableState<Boolean> = mutableStateOf(false)

    fun initiate() {
        logger.d("init")
//        scheduleSynchronization()
        observeLocalNostrFeed()
//        observeSynchronizeNostrEventRequestByPurposeAndStatusCount()
    }
    fun observeLocalNostrFeed() {
        logger.d("observeLocalNostrFeed")
        viewModelScope.launch(Dispatchers.IO) {
            nostrRepository.observeProfileWithFollowing(
                publicKey
            ).collect {
                messageListUIState = MessageListUIState.Loaded(
                    messageList = emptyList()
                )
            }
        }
    }

    fun scheduleSynchronization() {
        logger.d("scheduleSynchronization")
        viewModelScope.launch(Dispatchers.IO) {
            // Sync Notifications... might want to also run this in the background
            nostrRepository.queueNegentropySynchronizeRequest(
                Relays.negentropicRelaySet.shuffled().map { normalizedRelayUrl ->
                    NegentropySynchronizeRequest(
                        id = NegentropySynchronizeRequest.computeId(
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
        onNavigateToEvent: (Route) -> Unit
    ) {

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val feedListUIState = messageListUIState) {
                MessageListUIState.Error -> {
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
                is MessageListUIState.Loaded -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Messages"
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {

                            }
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "Steve Biko"
                                )
                            }

                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {

                            }
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Chris Hani"
                                )
                            }

                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {

                            }
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Desmond Tutu"
                                )
                            }

                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {

                            }
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Hip Hop Pantsula"
                                )
                            }

                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {

                            }
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Knee Cap"
                                )
                            }
                        }
                    }
//                    if (feedListUIState.messageList.isEmpty()) {
//                        Column(
//                            modifier = Modifier.fillMaxWidth(),
//                            horizontalAlignment = Alignment.CenterHorizontally
//                        )  {
//                            Spacer(
//                                modifier = Modifier.height(50.dp)
//                            )
//                            Text(
//                                text = "No events were found",
//                            )
//                        }
//                    } else {
//                        LazyColumn(
//                            modifier = Modifier.fillMaxWidth(),
//                            horizontalAlignment = Alignment.CenterHorizontally,
//                            verticalArrangement = Arrangement.spacedBy(10.dp)
//                        ) {
//                            items(
//                                items = feedListUIState.messageList,
//                                key = { profile -> profile.publicKey }
//                            ) { profile ->
//                                profile.RenderAsListItem(
//                                    onNavigateToEvent = onNavigateToEvent
//                                )
//                            }
//                        }
//                    }

                }
                MessageListUIState.Loading -> {
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
        const val TAG = "MessageListViewModel"

        fun factory(
            initialMessageListUIState: MessageListUIState = MessageListUIState.Loading,
            publicKey: String,
            synchronizationFilter: SynchronizationFilter,
            nostrRepository: NostrRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MessageListViewModel(
                    initialMessageListUIState = initialMessageListUIState,
                    publicKey = publicKey,
                    synchronizationFilter = synchronizationFilter,
                    nostrRepository = nostrRepository
                )
            }
        }
    }
}