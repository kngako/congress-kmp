package at.torch.compose.ui.view.model

import at.torch.compose.database.model.types.SynchronizationFilter
import androidx.compose.foundation.pager.PagerState
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
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

enum class HomeScreenType {
    Following, Mentions, Messages
}

class HomeViewModel(
    val activeUserPublicKey: HexKey,
    val initialHomeScreenUIState: at.torch.compose.ui.view.state.HomeScreenUIState,
    val nostrRepository: at.torch.compose.repository.NostrRepository,
    val pagerState: PagerState
): ViewModel() {

    val logger = Logger.withTag(TAG)


    var homeScreenUIState: at.torch.compose.ui.view.state.HomeScreenUIState by mutableStateOf(initialHomeScreenUIState)
        private set

    fun initiate() {
        observeActiveUserProfileWithFollowing()
    }

    fun observeActiveUserProfileWithFollowing() {
        logger.d("observeActiveUserProfileWithFollowing")
        viewModelScope.launch(Dispatchers.IO) {
            nostrRepository.observeProfileWithFollowing(
                activeUserPublicKey
            ).collect { profileWithFollowing ->
                homeScreenUIState = if (profileWithFollowing != null) {
                    _root_ide_package_.at.torch.compose.ui.view.state.HomeScreenUIState.Loaded(
                        profileWithFollowing = profileWithFollowing
                    )
                } else {
                    _root_ide_package_.at.torch.compose.ui.view.state.HomeScreenUIState.Error
                }
            }
        }
    }

    fun getSynchronizationFilter(
        homeScreenType: HomeScreenType,
        profileWithFollowing: at.torch.compose.database.model.intermdiate.LocalProfileWithFollowing,
    ): at.torch.compose.database.model.types.SynchronizationFilter {

        val twelveHoursAgo = Clock.System.now().minus(12.hours)
        val now = Clock.System.now()

        return when (homeScreenType) {
            HomeScreenType.Following -> {
                _root_ide_package_.at.torch.compose.database.model.types.SynchronizationFilter(
                    authors = profileWithFollowing.following.map { it.publicKey }.toTypedArray(),
                    kinds = arrayOf(
                        TextNoteEvent.KIND,
                        RepostEvent.KIND,
                    ),
                    since = twelveHoursAgo,
                    until = now,
                    limit = 50
                )
            }
            HomeScreenType.Mentions -> {
                _root_ide_package_.at.torch.compose.database.model.types.SynchronizationFilter(
                    kinds = arrayOf(
                        TextNoteEvent.KIND,
                        RepostEvent.KIND,
                        ReactionEvent.KIND,
                        LnZapEvent.KIND,
                        ContactListEvent.KIND,
                        AdvertisedRelayListEvent.KIND,
                        PrivateDmEvent.KIND,
                        BlossomServersEvent.KIND,
                        FileServersEvent.KIND
                    ),
                    tags = mapOf(
                        Pair("p", listOf(activeUserPublicKey))
                    ),
                    since = twelveHoursAgo,
                    until = now,
                    limit = 50
                )
            }
            HomeScreenType.Messages -> {
                _root_ide_package_.at.torch.compose.database.model.types.SynchronizationFilter(
                    kinds = arrayOf(
                        GiftWrapEvent.KIND,
                    ),
                    tags = mapOf(
                        Pair("p", listOf(activeUserPublicKey))
                    ),
                    limit = 50
                )
            }
        }
    }

    companion object {
        const val TAG = "HomeViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            initialHomeScreenUIState: at.torch.compose.ui.view.state.HomeScreenUIState,
            nostrRepository: at.torch.compose.repository.NostrRepository,
            pagerState: PagerState
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    initialHomeScreenUIState = initialHomeScreenUIState,
                    nostrRepository = nostrRepository,
                    pagerState = pagerState,
                )
            }
        }
    }
}