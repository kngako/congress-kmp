package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.database.model.Connection
import ac.cord.auxiliary.compose.database.model.UnsignedNostrEvent
import ac.cord.auxiliary.compose.database.model.types.SynchronizationFilter
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.view.state.MetadataEventDetailUIState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.MutableState
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
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip02FollowList.RelaySet
import com.vitorpamplona.quartz.nip02FollowList.tags.ContactTag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayFeedsListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

enum class MetadataEventDetailType {
    Posts, Replies, Articles, Followers, Following, Zaps, Photos, Shorts, Videos,  Bookmarks, Reports, Relays
}

class MetadataEventDetailViewModel(
    initialMetadataEventDetailUIState: MetadataEventDetailUIState = MetadataEventDetailUIState.Loading,
    val activeUserPublicKey: HexKey,
    val eventPublicKey: HexKey,
    val nostrRepository: NostrRepository,
    val pagerState: PagerState
): ViewModel() {

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    var metadataEventDetailUIState: MetadataEventDetailUIState by mutableStateOf(initialMetadataEventDetailUIState)
        private set

    val logger = Logger.withTag("MetadataEventDetailViewModel")

    fun initiate() {
        observeMetadataEventRelationWithActiveUser()
    }

    fun observeMetadataEventRelationWithActiveUser() {
        logger.d("observeLocalNostrFeed")
        viewModelScope.launch(Dispatchers.IO) {

            nostrRepository.observeRelation(
                eventPublicKey,
                activePublicKey = activeUserPublicKey
            ).collect { relations ->
                logger.d("We found relations: $relations")

                metadataEventDetailUIState = MetadataEventDetailUIState.Loaded(
                    isActiveUser = isActiveUser(activeUserPublicKey),
                    followingActiveUserConnection = relations.find { connection -> connection.sourcePublicKey == eventPublicKey && connection.destinationPublicKey == activeUserPublicKey },
                    followedByActiveUserConnection = relations.find { connection -> connection.sourcePublicKey == activeUserPublicKey && connection.destinationPublicKey == eventPublicKey }
                )

                if (isActionPending.value) {
                    // This means this new relation was caused by user action...
                    isActionPending.value = false
                }
            }
        }
    }

    fun isActiveUser(activePublicKey: HexKey): Boolean {
        return activePublicKey == eventPublicKey
    }

    fun isActiveUser(): Boolean {
        return isActiveUser(
            activePublicKey = activeUserPublicKey
        )
    }

    fun getSynchronizationFilter(
        metadataEventDetailType: MetadataEventDetailType
    ) = when (metadataEventDetailType) {
        MetadataEventDetailType.Posts -> {
            SynchronizationFilter(
                authors = arrayOf(
                    eventPublicKey
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
                    eventPublicKey
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
                    eventPublicKey
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
                    eventPublicKey
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
                    eventPublicKey
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
                    eventPublicKey
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
                    eventPublicKey
                ),
                kinds = arrayOf(
                    PictureEvent.KIND,
                ),
                limit = 50
            )
        }
        MetadataEventDetailType.Shorts -> {
            SynchronizationFilter(
                authors = arrayOf(
                    eventPublicKey
                ),
                kinds = arrayOf(
                    VideoShortEvent.KIND,
                ),
                limit = 50
            )
        }
        MetadataEventDetailType.Videos -> {
            SynchronizationFilter(
                authors = arrayOf(
                    eventPublicKey
                ),
                kinds = arrayOf(
                    VideoNormalEvent.KIND,
                ),
                limit = 50
            )
        }
        MetadataEventDetailType.Bookmarks -> {
            SynchronizationFilter(
                authors = arrayOf(
                    eventPublicKey
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
                    eventPublicKey
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
                    eventPublicKey
                ),
                kinds = arrayOf(
                    RelayFeedsListEvent.KIND,
                ),
                limit = 50
            )
        }
    }

    fun follow() {
        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val contactListEvent = nostrRepository.getNostrEvent(
                publicKey = activeUserPublicKey,
                kind = ContactListEvent.KIND
            )

            logger.d("ContactListEvent: $contactListEvent")

            val followUsers: TagArray = if (contactListEvent != null) {
                if (contactListEvent.tags.isTaggedUser(eventPublicKey)) {
                    // User already being followed
                    logger.d("User is already following $eventPublicKey")
                    return@launch
                }

                contactListEvent.tags.plus(
                    ContactTag(
                        pubKey = eventPublicKey
                    ).toTagArray()
                )
            } else {
                arrayOf(
                    ContactTag(activeUserPublicKey, null, null).toTagArray(),
                    ContactTag(eventPublicKey, null, null).toTagArray(),
                )
            }
            val contactListTagList = listOf(AltTag.assemble(ContactListEvent.ALT)) +
                    followUsers.map { it }

            nostrRepository.saveUnsignedNostrEvent(
                UnsignedNostrEvent(
                    pubKey = activeUserPublicKey,
                    kind = ContactListEvent.KIND,
                    tags = contactListTagList.toTypedArray(),
                    content = RelaySet.assemble(
                        emptyMap()
                    )
                )
            )
        }
    }

    fun unfollow(connection: Connection) {
        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val contactListEvent = nostrRepository.getNostrEvent(
                publicKey = activeUserPublicKey,
                kind = ContactListEvent.KIND
            )


            if (contactListEvent != null) {
                if (!contactListEvent.tags.isTaggedUser(eventPublicKey)) {
                    logger.d("User is not being followed so no need to unfollow: $eventPublicKey")
                } else {
                    nostrRepository.saveUnsignedNostrEvent(
                        UnsignedNostrEvent(
                            pubKey = activeUserPublicKey,
                            kind = ContactListEvent.KIND,
                            tags = contactListEvent.tags.filter { it.size > 1 && it[1] != eventPublicKey }.toTypedArray(),
                            content = RelaySet.assemble(
                                emptyMap()
                            )
                        )
                    )
                }

                nostrRepository.deleteConnection(
                    connection
                )
            }
        }
    }
    companion object {
        const val TAG = "MetadataEventDetailViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            publicKey: HexKey,
            nostrRepository: NostrRepository,
            pagerState: PagerState
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MetadataEventDetailViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    nostrRepository = nostrRepository,
                    pagerState = pagerState,
                    eventPublicKey = publicKey,
                )
            }
        }
    }
}