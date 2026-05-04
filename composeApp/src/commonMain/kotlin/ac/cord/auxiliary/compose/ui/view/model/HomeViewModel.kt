package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfileWithFollowing
import ac.cord.auxiliary.compose.database.model.types.SynchronizationFilter
import ac.cord.auxiliary.compose.managers.SeedManager
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.view.state.HomeScreenUIState
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
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

enum class HomeScreenType {
    Following, Mentions, Messages
}

class HomeViewModel(
    val initialHomeScreenUIState: HomeScreenUIState,
    val nostrRepository: NostrRepository,
    val pagerState: PagerState
): ViewModel() {

    val logger = Logger.withTag(TAG)


    var homeScreenUIState: HomeScreenUIState by mutableStateOf(initialHomeScreenUIState)
        private set

    fun initiate() {
        observeActiveUserProfileWithFollowing()
    }

    fun observeActiveUserProfileWithFollowing() {
        logger.d("observeActiveUserProfileWithFollowing")
        viewModelScope.launch(Dispatchers.IO) {
            val activePublicKey = SeedManager.activePublicKey().toHexKey()

            nostrRepository.observeProfileWithFollowing(
                activePublicKey
            ).collect { profileWithFollowing ->
                homeScreenUIState = if (profileWithFollowing != null) {
                    HomeScreenUIState.Loaded(
                        profileWithFollowing = profileWithFollowing
                    )
                } else {
                    HomeScreenUIState.Error
                }
            }
        }
    }

    fun getSynchronizationFilter(
        homeScreenType: HomeScreenType,
        profileWithFollowing: LocalProfileWithFollowing,
    ): SynchronizationFilter {
        val publicKey = SeedManager.activePublicKey().toHexKey()

        val threeDaysAgo = Clock.System.now().minus(3.days)
        val now = Clock.System.now()

        return when (homeScreenType) {
            HomeScreenType.Following -> {
                SynchronizationFilter(
                    authors = profileWithFollowing.following.map { it.publicKey }.toTypedArray(),
                    kinds = arrayOf(
                        TextNoteEvent.KIND,
                        RepostEvent.KIND,
                    ),
                    since = threeDaysAgo,
                    until = now
                )
            }
            HomeScreenType.Mentions -> {
                SynchronizationFilter(
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
                        Pair("p", listOf(publicKey))
                    ),
                    since = threeDaysAgo,
                    until = now
                )
            }
            HomeScreenType.Messages -> {
                SynchronizationFilter(
                    authors = arrayOf(
                        publicKey
                    ),
                    kinds = arrayOf(
                        LongTextNoteEvent.KIND,
                    ),
                    since = threeDaysAgo,
                    until = now
                )
            }
        }
    }

    companion object {
        const val TAG = "HomeViewModel"

        fun factory(
            initialHomeScreenUIState: HomeScreenUIState,
            nostrRepository: NostrRepository,
            pagerState: PagerState
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    initialHomeScreenUIState = initialHomeScreenUIState,
                    nostrRepository = nostrRepository,
                    pagerState = pagerState,
                )
            }
        }
    }
}