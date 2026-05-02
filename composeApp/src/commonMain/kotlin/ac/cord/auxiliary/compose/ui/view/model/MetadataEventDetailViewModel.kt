package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.database.model.Connection
import ac.cord.auxiliary.compose.database.model.UnsignedNostrEvent
import ac.cord.auxiliary.compose.database.model.types.SynchronizationFilter
import ac.cord.auxiliary.compose.managers.SeedManager
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
    val publicKey: HexKey,
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
            val activePublicKey = SeedManager.activePublicKey().toHexKey()

            nostrRepository.observeRelation(
                publicKey,
                activePublicKey = activePublicKey
            ).collect { relations ->
                logger.d("We found relations: $relations")

                metadataEventDetailUIState = MetadataEventDetailUIState.Loaded(
                    isActiveUser = activePublicKey == publicKey,
                    followingActiveUserConnection = relations.find { connection -> connection.sourcePublicKey == publicKey && connection.destinationPublicKey == activePublicKey },
                    followedByActiveUserConnection = relations.find { connection -> connection.sourcePublicKey == activePublicKey && connection.destinationPublicKey == publicKey }
                )

                if (isActionPending.value) {
                    // This means this new relation was caused by user action...
                    isActionPending.value = false
                }
            }
        }
    }

    fun getSynchronizationFilter(
        metadataEventDetailType: MetadataEventDetailType
    ) = when (metadataEventDetailType) {
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
                    PictureEvent.KIND,
                ),
                limit = 50
            )
        }
        MetadataEventDetailType.Shorts -> {
            SynchronizationFilter(
                authors = arrayOf(
                    publicKey
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
                    publicKey
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

    fun follow() {
        isActionPending.value = true

        val activePublicKey = SeedManager.activePublicKey().toHexKey()

        viewModelScope.launch(Dispatchers.IO) {
            val contactListEvent = nostrRepository.getNostrEvent(
                publicKey = activePublicKey,
                kind = ContactListEvent.KIND
            )

            logger.d("ContactListEvent: $contactListEvent")

            val followUsers: TagArray = if (contactListEvent != null) {
                if (contactListEvent.tags.isTaggedUser(publicKey)) {
                    // User already being followed
                    logger.d("User is already following $publicKey")
                    return@launch
                }

                contactListEvent.tags.plus(
                    ContactTag(
                        pubKey = publicKey
                    ).toTagArray()
                )
            } else {
                arrayOf(
                    ContactTag(activePublicKey, null, null).toTagArray(),
                    ContactTag(publicKey, null, null).toTagArray(),
                )
            }
            val contactListTagList = listOf(AltTag.assemble(ContactListEvent.ALT)) +
                    followUsers.map { it }

            nostrRepository.saveUnsignedNostrEvent(
                UnsignedNostrEvent(
                    pubKey = activePublicKey,
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

        val activePublicKey = SeedManager.activePublicKey().toHexKey()

        viewModelScope.launch(Dispatchers.IO) {
            val contactListEvent = nostrRepository.getNostrEvent(
                publicKey = activePublicKey,
                kind = ContactListEvent.KIND
            )


            if (contactListEvent != null) {
                if (!contactListEvent.tags.isTaggedUser(publicKey)) {
                    logger.d("User is not being followed so no need to unfollow: $publicKey")
                } else {
                    nostrRepository.saveUnsignedNostrEvent(
                        UnsignedNostrEvent(
                            pubKey = activePublicKey,
                            kind = ContactListEvent.KIND,
                            tags = contactListEvent.tags.filter { it.size > 1 && it[1] != publicKey }.toTypedArray(),
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
            publicKey: HexKey,
            nostrRepository: NostrRepository,
            pagerState: PagerState
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MetadataEventDetailViewModel(
                    nostrRepository = nostrRepository,
                    pagerState = pagerState,
                    publicKey = publicKey
                )
            }
        }
    }
}