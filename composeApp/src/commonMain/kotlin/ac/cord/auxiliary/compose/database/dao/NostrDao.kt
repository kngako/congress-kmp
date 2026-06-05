package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.AuxDatabase
import ac.cord.auxiliary.compose.database.GENESIS_AT
import ac.cord.auxiliary.compose.database.model.BroadcastNostrEventRequest
import ac.cord.auxiliary.compose.database.model.ChatMessage
import ac.cord.auxiliary.compose.database.model.ChatRoom
import ac.cord.auxiliary.compose.database.model.Connection
import ac.cord.auxiliary.compose.database.model.Mention
import ac.cord.auxiliary.compose.database.model.NegentropySynchronizeRequest
import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.NostrEventRelay
import ac.cord.auxiliary.compose.database.model.Participant
import ac.cord.auxiliary.compose.database.model.Post
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.UnsignedNostrEvent
import ac.cord.auxiliary.compose.database.model.types.SynchronizationFilter
import ac.cord.auxiliary.compose.exceptions.GiftWrapImpersonationException
import ac.cord.auxiliary.compose.managers.SeedManager
import ac.cord.auxiliary.compose.managers.toHex
import androidx.room3.Dao
import androidx.room3.Transaction
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.events.taggedEvents
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUsers
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlin.math.log
import kotlin.time.Clock
import kotlin.time.Instant

@Dao
abstract class NostrDao(
    private val database: AuxDatabase
) {
    val logger = Logger.withTag("NostrDao")

    val profileEventKinds = arrayOf(
        MetadataEvent.KIND,
        ContactListEvent.KIND,
        ChatMessageRelayListEvent.KIND,
//        AdvertisedRelayListEvent.KIND,
//        SearchRelayListEvent.KIND,
//        IndexerRelayListEvent.KIND,
//        ChannelListEvent.KIND,
//        RelayFeedsListEvent.KIND
    )

    @Transaction
    open suspend fun publishNostrEvent(
        unsignedNostrEvent: UnsignedNostrEvent,
        nostrEvent: NostrEvent,
        relayURLs: List<String>
    ) {
        logger.i("Publish Nostr Event: $nostrEvent ($relayURLs)")
        // Update unsignedEvent with signedTime time...
        database.unsignedNostrEventDao().upsert(
            unsignedNostrEvent.copy(
                signedAt = Clock.System.now()
            )
        )

        database.nostrEventDao().upsert(nostrEvent)

        indexNostrEvent(
            nostrEvent = nostrEvent,
            relayURL = relayURLs.first(),
            synchronizationRelayURLs = relayURLs,
            level = 0
        )

        relayURLs.forEach { relayURL ->
            database.broadcastNostrEventRequestDao().upsert(
                BroadcastNostrEventRequest(
                    nostrEventId = nostrEvent.id,
                    unsignedNostrEventId = nostrEvent.unsignedNostrEventId,
                    relayURL = relayURL
                )
            )
        }
    }

    @Transaction
    open suspend fun storeNostrEvent(
        nostrEvent: NostrEvent,
        relayURL: String,
        synchronizationRelayURLs: List<String>,
        level: Int
    ) {
        val storedNostrEvent = database.nostrEventDao().getNostrEventById(nostrEvent.id)

        if (storedNostrEvent == null) {
            // Add new nostr event
            logger.i("Store Nostr Event: $nostrEvent")
            database.nostrEventDao().insert(nostrEvent)


        } else if (nostrEvent.createdAt > storedNostrEvent.createdAt) {
            // Update nostr event`
            logger.i("Update Nostr Event: $nostrEvent")
            database.nostrEventDao().upsert(nostrEvent)
        } else {
            // Nothing else needs to be done
            val profile = database.profileDao().getProfileByPublicKey(nostrEvent.pubKey)
            if (profile?.createdAt == GENESIS_AT && nostrEvent.kind == MetadataEvent.KIND) {
                logger.i("This is a placeholder profile might need to get synced...: ${profile.publicKey}")
                // Setup the sync here...
            } else {
                return
            }
        }

        database.nostrEventRelayDao().upsert(
            NostrEventRelay(
                relayURL = relayURL,
                nostrEventId = nostrEvent.id
            )
        )

        // Index nostrEvent
        logger.i("Index Nostr Event: ${nostrEvent.id}")
        indexNostrEvent(
            nostrEvent = nostrEvent,
            relayURL = relayURL,
            synchronizationRelayURLs = synchronizationRelayURLs,
            level = level
        )
    }

    private suspend fun indexNostrEvent(
        nostrEvent: NostrEvent,
        relayURL: String,
        synchronizationRelayURLs: List<String>,
        level: Int
    ) {
        val profilePublicKeysToSync = mutableMapOf<String, MutableSet<String>>()
        val eventIdsToSync = mutableMapOf<String, MutableSet<String>>()

        if (nostrEvent.unsignedNostrEventId == null && nostrEvent.kind != GiftWrapEvent.KIND) {
            // Find or create profile with the pubKey... if not found submit a sync request...
            val profile = database.profileDao().getProfileByPublicKey(nostrEvent.pubKey)

            if (profile == null) {
                val placeHolderProfile = Profile(
                    displayName = "LOADING...",
                    publicKey = nostrEvent.pubKey,
                    createdAt = GENESIS_AT,
                    nostrEventId = nostrEvent.id,  // Will get overwriting by sync,
                )
                database.profileDao().insertPlaceholderProfile(placeHolderProfile)
                if (profilePublicKeysToSync[relayURL] == null) {
                    profilePublicKeysToSync[relayURL] = mutableSetOf()
                }
                profilePublicKeysToSync[relayURL]?.add(nostrEvent.pubKey)
            } else if (profile.createdAt == GENESIS_AT && level == 0) {
                logger.i("This is a placeholder profile... that might need to get synced...: $profile")
                if (profilePublicKeysToSync[relayURL] == null) {
                    profilePublicKeysToSync[relayURL] = mutableSetOf()
                }
                profilePublicKeysToSync[relayURL]?.add(nostrEvent.pubKey)
            }
        } else {
            logger.w("We somehow have an unsignedNostrEvent/giftwrap event: $nostrEvent")
        }

        // Index reposts first because they have the contained posts...
        nostrEvent.toRepostedRelationWithContainedPost()?.let { (repostedRelation, containedPost) ->
            // Find or create placeHolder note that is being reposted
            val repostedNostrEvent = database.nostrEventDao().getNostrEventById(repostedRelation.repostedNostrEventId)

            val repostedProfilePublicKey = if (repostedNostrEvent == null) {
                // Sync event...
                if (eventIdsToSync[relayURL] == null) {
                    eventIdsToSync[relayURL] = mutableSetOf()
                }
                eventIdsToSync[relayURL]?.add(repostedRelation.repostedNostrEventId)

                database.nostrEventDao().insert(
                    if (containedPost != null) {
                        NostrEvent(
                            id = containedPost.id,
                            content = containedPost.content,
                            createdAt = Instant.fromEpochSeconds(containedPost.createdAt),
                            pubKey = containedPost.pubKey,
                            sig = containedPost.sig,
                            kind = containedPost.kind,
                            tags = containedPost.tags
                        )
                    } else {
                        NostrEvent(
                            id = repostedRelation.repostedNostrEventId,
                            content = "Pending Sync",
                            createdAt = GENESIS_AT,
                            pubKey = nostrEvent.pubKey,
                            sig = "",
                            kind = nostrEvent.kind,
                            tags = emptyArray()
                        )
                    }
                )

                repostedRelation.repostedProfilePublicKey
            } else {
                repostedRelation.repostedProfilePublicKey ?: repostedNostrEvent.pubKey
            }

            val repostedProfile = repostedProfilePublicKey?.let { database.profileDao().getProfileByPublicKey(it) }

            if (repostedProfilePublicKey != null && repostedProfile == null) {
                database.profileDao().insertPlaceholderProfile(
                    Profile(
                        displayName = "LOADING...",
                        publicKey = repostedProfilePublicKey,
                        createdAt = GENESIS_AT,
                        nostrEventId = nostrEvent.id,  // Will get overwriting by sync,
                    )
                )
            }
            database.repostedRelationDao().upsert(
                repostedRelation.copy(
                    repostedProfilePublicKey = repostedProfilePublicKey
                )
            )
        }


        // Sync tagged events and authors...
        nostrEvent.tags.taggedEvents().take(4).forEach { taggedEvent -> // We only look at the first 3 tagged events...
            val taggedNostrEvent = database.nostrEventDao().getNostrEventById(taggedEvent.eventId)
            if (taggedNostrEvent == null) {
                database.nostrEventDao().insert(
                    NostrEvent(
                        id = taggedEvent.eventId,
                        content = "Pending Sync... ",
                        createdAt = GENESIS_AT,
                        pubKey = nostrEvent.pubKey,
                        sig = "",
                        kind = nostrEvent.kind,
                        tags = emptyArray()
                    )
                )

                val recommendRelayUrl = taggedEvent.relay?.url

                if (recommendRelayUrl != null) {
                    if (eventIdsToSync[recommendRelayUrl] == null) {
                        eventIdsToSync[recommendRelayUrl] = mutableSetOf()
                    }
                    eventIdsToSync[recommendRelayUrl]?.add(taggedEvent.eventId)
                } else {
                    if (eventIdsToSync[relayURL] == null) {
                        eventIdsToSync[relayURL] = mutableSetOf()
                    }
                    eventIdsToSync[relayURL]?.add(taggedEvent.eventId)

                }
            }

        }
        nostrEvent.tags.taggedUsers().take(5).forEach { taggedUser -> // 5 tagged users...
            val recommendedRelay = taggedUser.relayHint?.url

            val taggedProfile = database.profileDao().getProfileByPublicKey(taggedUser.pubKey)

            if (taggedProfile == null) {
                // Save a placeholder
                database.profileDao().insertPlaceholderProfile(
                    Profile(
                        displayName = "LOADING...",
                        publicKey = taggedUser.pubKey,
                        createdAt = GENESIS_AT,
                        nostrEventId = nostrEvent.id,  // Will get overwriting by sync,
                    )
                )
            }

            database.mentionDao().insert(
                Mention(
                    mentionedPublicKey = taggedUser.pubKey,
                    mentioningNostrEventId = nostrEvent.id,
                    relayUrl = taggedUser.relayHint?.url
                )
            )
            if (recommendedRelay != null) {
                if (profilePublicKeysToSync[recommendedRelay] == null) {
                    profilePublicKeysToSync[recommendedRelay] = mutableSetOf()
                }
                profilePublicKeysToSync[recommendedRelay]?.add(taggedUser.pubKey)
            } else {
                if (profilePublicKeysToSync[relayURL] == null) {
                    profilePublicKeysToSync[relayURL] = mutableSetOf()
                }
                profilePublicKeysToSync[relayURL]?.add(
                    taggedUser.pubKey
                )
            }
        }

        nostrEvent.toProfile()?.let { profile ->
            database.profileDao().upsert(profile)
        }

        nostrEvent.toGiftWrapMessageWithReceiverPTag()?.let { giftWrapMessage ->
            logger.d("giftWrapMessage: $giftWrapMessage")
            val giftWrapReceiverProfile = database.profileDao().getProfileByPublicKey(giftWrapMessage.receiverPublicKey)

            if (giftWrapReceiverProfile == null) {
                // Save a placeholder
                database.profileDao().insertPlaceholderProfile(
                    Profile(
                        displayName = "LOADING...",
                        publicKey = giftWrapMessage.receiverPublicKey,
                        createdAt = GENESIS_AT,
                        nostrEventId = nostrEvent.id,  // Will get overwriting by sync,
                    )
                )

                val recommendRelayUrl = giftWrapMessage.receiverRelayHit

                if (recommendRelayUrl != null) {
                    if (profilePublicKeysToSync[recommendRelayUrl] == null) {
                        profilePublicKeysToSync[recommendRelayUrl] = mutableSetOf()
                    }
                    profilePublicKeysToSync[recommendRelayUrl]?.add(giftWrapMessage.receiverPublicKey)
                } else {
                    if (profilePublicKeysToSync[relayURL] == null) {
                        profilePublicKeysToSync[relayURL] = mutableSetOf()
                    }
                    profilePublicKeysToSync[relayURL]?.add(
                        giftWrapMessage.receiverPublicKey
                    )
                }
            }


            database.giftWrapMessageDao().upsert(
                giftWrapMessage
            )

            // TODO: Might want to process this somewhere else where privateKey is available...
            SeedManager.activeKeyPair().privKey?.let { activeUserPrivateKey ->
                giftWrapMessage.decryptGiftWrapSeal(
                    activeUserPrivateKey
                )?.let { giftWrapSeal ->
                    database.giftWrapSealDao().upsert(
                        giftWrapSeal
                    )

                    giftWrapSeal.decryptGiftWrapPayload(
                        activeUserPrivateKey
                    )?.let { decryptedGiftWrapPayload ->
                        if (giftWrapSeal.publicKey.lowercase() != decryptedGiftWrapPayload.publicKey.lowercase()) {
                            val giftWrapImpersonation = GiftWrapImpersonationException(
                                "Seal pubkey (${giftWrapSeal.publicKey}) and payload pubkey (${decryptedGiftWrapPayload.publicKey}) needs to be the same for the giftWrap ${nostrEvent.id}"
                            )
                            logger.e("Impersonation", giftWrapImpersonation)
                            throw giftWrapImpersonation // Once this is thrown the database transaction fails and nothing gets saved to the DB...
                        }
                        val result = database.giftWrapPayloadDao().upsert(
                            decryptedGiftWrapPayload
                        )

                        val giftWrapPayload = if (result != -1L) {
                            decryptedGiftWrapPayload.copy(
                                id = result
                            )
                        } else {
                            decryptedGiftWrapPayload
                        }

                        val chatRoomId = giftWrapPayload.aggregatedParticipantsPublicKey()
                        logger.d("ChatRoomId: $chatRoomId")

                        if (chatRoomId != null) {
                            // Find or create chatRoom
                            val localChatRoom = database.chatRoomDao().findChatRoomById(
                                chatRoomId
                            )

                            if (localChatRoom == null) {
                                val userPublicKey = SeedManager.activeKeyPair().pubKey.toHex()
                                database.chatRoomDao().upsert(
                                    ChatRoom(
                                        id = chatRoomId,
                                        userPublicKey = userPublicKey,
                                        subject = decryptedGiftWrapPayload.parseSubject(),
                                        createdAt = decryptedGiftWrapPayload.createdAt,
                                        initialGiftWrapPayloadId = giftWrapPayload.id
                                    )
                                )

                                // Add participants...
                                val participants = giftWrapPayload.participantPTags(
                                    NormalizedRelayUrl(relayURL) // TODO: Get relayURL for publicKey profile...
                                ).map {
                                    Participant(
                                        participantPublicKey = it.pubKey,
                                        chatRoomId = chatRoomId,
                                        relayHint = it.relayHint?.url
                                    )
                                }

                                // Sync missing participant profiles...
                                participants.forEach { participant ->
                                    val giftWrapParticipantProfile = database.profileDao().getProfileByPublicKey(participant.participantPublicKey)

                                    if (giftWrapParticipantProfile == null) {
                                        // Save a placeholder
                                        database.profileDao().insertPlaceholderProfile(
                                            Profile(
                                                displayName = "LOADING...",
                                                publicKey = participant.participantPublicKey,
                                                createdAt = GENESIS_AT,
                                                nostrEventId = nostrEvent.id,  // Will get overwriting by sync,
                                            )
                                        )

                                        val recommendRelayUrl = giftWrapMessage.receiverRelayHit

                                        if (recommendRelayUrl != null) {
                                            if (profilePublicKeysToSync[recommendRelayUrl] == null) {
                                                profilePublicKeysToSync[recommendRelayUrl] = mutableSetOf()
                                            }
                                            profilePublicKeysToSync[recommendRelayUrl]?.add(participant.participantPublicKey)
                                        } else {
                                            if (profilePublicKeysToSync[relayURL] == null) {
                                                profilePublicKeysToSync[relayURL] = mutableSetOf()
                                            }
                                            profilePublicKeysToSync[relayURL]?.add(
                                                participant.participantPublicKey
                                            )
                                        }
                                    }
                                }

                                database.participantDao().upsert(
                                    participants
                                )

                                participants.filter { it.participantPublicKey != userPublicKey }.forEach { participant ->
                                    val publicKey = participant.participantPublicKey

                                    val chatMessageRelayListEvent = database.nostrEventDao().getAuthoredNostrEvents(
                                        kinds = arrayOf(
                                            ChatMessageRelayListEvent.KIND
                                        ),
                                        authors = arrayOf(
                                            publicKey
                                        ),
                                        since = GENESIS_AT,
                                        limit = 5
                                    ).firstOrNull()?.let { nostrEvent ->
                                        if (nostrEvent.kind != ChatMessageRelayListEvent.KIND) {
                                            logger.e("getAuthoredNostrEvents returned invalid ChatMessageRelayListEvent for ${publicKey}: $nostrEvent")
                                            null
                                        } else {
                                            ChatMessageRelayListEvent(
                                                id = nostrEvent.id,
                                                tags = nostrEvent.tags,
                                                pubKey = nostrEvent.pubKey,
                                                content = nostrEvent.content,
                                                createdAt = nostrEvent.createdAt.epochSeconds,
                                                sig = nostrEvent.sig
                                            )
                                        }
                                    }

                                    if (chatMessageRelayListEvent != null) {
                                        // Sync messages from this relay that were sent by us
                                        val synchronizationFilter = SynchronizationFilter(
                                            kinds = arrayOf(
                                                GiftWrapEvent.KIND,
                                            ),
                                            authors = arrayOf(
                                                userPublicKey
                                            ),
                                            tags = mapOf(
                                                Pair("p", listOf(participant.participantPublicKey))
                                            ),
                                            limit = 50
                                        )
                                        database.negentropySynchronizeRequestDao().insert(
                                            chatMessageRelayListEvent.relays().map { normalizedRelayUrl ->
                                                NegentropySynchronizeRequest(
                                                    id = NegentropySynchronizeRequest.computeId(
                                                        relayURL = normalizedRelayUrl.url,
                                                        synchronizationFilter = synchronizationFilter
                                                    ),
                                                    purpose = "sent-messages",
                                                    synchronizationFilter = synchronizationFilter,
                                                    relayURL = normalizedRelayUrl.url,
                                                    level = 0
                                                )
                                            }
                                        )
                                    } else {
                                        logger.w("We don't have a chatMessageRelayListEvent for the pubkey $publicKey")

                                        // Sync ChatMessageRelayListEvent publicKey...
                                        // TODO: Get relayHint form participant...
                                        if (profilePublicKeysToSync.containsKey(relayURL)) {
                                            profilePublicKeysToSync[relayURL] = mutableSetOf()
                                        }
                                        profilePublicKeysToSync[relayURL]?.add(participant.participantPublicKey)
                                    }
                                }
                            } else {
                                giftWrapPayload.parseSubject()?.let { subject ->
                                    database.chatRoomDao().upsert(
                                        localChatRoom.chatRoom.copy(
                                            subject = subject,
                                            updatedAt = giftWrapPayload.createdAt
                                        )
                                    )
                                }
                            }

                            database.chatMessageDao().upsert(
                                ChatMessage(
                                    giftWrapPayloadId = giftWrapPayload.id,
                                    senderPublicKey = giftWrapPayload.publicKey,
                                    isUserMessage = SeedManager.activePublicKey().toHex() == giftWrapPayload.publicKey,
                                    chatRoomId = chatRoomId,
                                    createdAt = giftWrapPayload.createdAt,
                                    content = giftWrapPayload.content,
                                )
                            )
                        } else {
                            logger.w("Failed to setup chatRoom for message")
                        }
                    }
                }
            }

        }

        nostrEvent.toPost()?.let { post ->
            if (post.replyToId != null) {
                // Find or create placeHolder note that is replyTo
                val replyingToPost = database.postDao().getPostById(post.replyToId)

                if (replyingToPost == null) {
                    // Persist PlaceHolder and sync
                    // TODO: Get replyingToAuthorPublicKey
                    database.postDao().insert(
                        Post(
                            id = post.replyToId,
                            createdAt = GENESIS_AT,
                            nostrEventId = nostrEvent.id, // Will get overwriting by sync handled by tagged events...
                            content = post.replyToId,
                            profilePublicKey = nostrEvent.pubKey // Will get overwriting by sync handled by tagged events...
                        )
                    )
                }
            }

            database.postDao().upsert(post)
        }

        nostrEvent.toReaction()?.let { reaction ->
            val replyingToPost = database.postDao().getPostById(reaction.replyToId)

            if (replyingToPost == null) {
                // Persist PlaceHolder and sync
                database.postDao().insert(
                    Post(
                        id = reaction.replyToId,
                        createdAt = GENESIS_AT,
                        nostrEventId = nostrEvent.id, // Will get overwriting by sync handled by tagged events...
                        content = reaction.replyToId,
                        profilePublicKey = nostrEvent.pubKey // Will get overwriting by sync handled by tagged events...
                    )
                )
            }
            database.reactionDao().upsert(reaction)
        }

        nostrEvent.toQuotedRelation()?.let { quotedRelation ->
            val quotedNostrEvent = database.nostrEventDao().getNostrEventById(quotedRelation.quotedNostrEventId)

            val quotedProfilePublicKey = if (quotedNostrEvent == null) {
                if (eventIdsToSync[relayURL] == null) {
                    eventIdsToSync[relayURL] = mutableSetOf()
                }
                eventIdsToSync[relayURL]?.add(quotedRelation.quotedNostrEventId)

                database.nostrEventDao().insert(
                    NostrEvent(
                        id = quotedRelation.quotedNostrEventId,
                        content = "Pending Sync",
                        createdAt = GENESIS_AT,
                        pubKey = nostrEvent.pubKey,
                        sig = "",
                        kind = nostrEvent.kind,
                        tags = emptyArray()
                    )
                )

                quotedRelation.quotedProfilePublicKey
            } else {
                quotedNostrEvent.pubKey
            }

            val quotedProfile = quotedProfilePublicKey?.let { database.profileDao().getProfileByPublicKey(it) }

            if (quotedProfilePublicKey != null && quotedProfile == null) {
                database.profileDao().insertPlaceholderProfile(
                    Profile(
                        displayName = "LOADING...",
                        publicKey = quotedProfilePublicKey,
                        createdAt = GENESIS_AT,
                        nostrEventId = nostrEvent.id,  // Will get overwriting by sync,
                    )
                )
            }

            // TODO: Might need to store placeholders
            database.quotedRelationDao().upsert(
                quotedRelation.copy(
                    quotedProfilePublicKey = quotedProfilePublicKey
                )
            )
        }

        nostrEvent.toInReplyToRelation()?.let { inReplyToRelation ->

            val inReplyToNostrEvent = database.nostrEventDao().getNostrEventById(inReplyToRelation.inReplyToNostrEventId)

            val inReplyToProfilePublicKey = if (inReplyToNostrEvent == null) {
                if (eventIdsToSync[relayURL] == null) {
                    eventIdsToSync[relayURL] = mutableSetOf()
                }
                eventIdsToSync[relayURL]?.add(inReplyToRelation.inReplyToNostrEventId)

                database.nostrEventDao().insert(
                    NostrEvent(
                        id = inReplyToRelation.inReplyToNostrEventId,
                        content = "Pending Sync",
                        createdAt = GENESIS_AT,
                        pubKey = nostrEvent.pubKey,
                        sig = "",
                        kind = nostrEvent.kind,
                        tags = emptyArray()
                    )
                )

                inReplyToRelation.inReplyToProfilePublicKey
            } else {
                inReplyToNostrEvent.pubKey
            }

            val inReplyToRootNostrEvent = inReplyToRelation.inReplyToRootNostrEventId?.let { database.nostrEventDao().getNostrEventById(it) }

            val inReplyToRootProfilePublicKey = if (inReplyToRootNostrEvent == null) {
                if (eventIdsToSync[relayURL] == null) {
                    eventIdsToSync[relayURL] = mutableSetOf()
                }
                eventIdsToSync[relayURL]?.add(inReplyToRelation.inReplyToNostrEventId)

                inReplyToRelation.inReplyToRootProfilePublicKey
                // Might need to save place holder...
            } else {
                inReplyToRootNostrEvent.pubKey
            }

            val inReplyToProfile = inReplyToRootProfilePublicKey?.let { database.profileDao().getProfileByPublicKey(it) }

            if (inReplyToRootProfilePublicKey != null && inReplyToProfile == null) {
                database.profileDao().insertPlaceholderProfile(
                    Profile(
                        displayName = "LOADING...",
                        publicKey = inReplyToRootProfilePublicKey,
                        createdAt = GENESIS_AT,
                        nostrEventId = nostrEvent.id,  // Will get overwriting by sync,
                    )
                )
            }

            database.inReplyToRelationDao().upsert(
                inReplyToRelation.copy(
                    inReplyToRootProfilePublicKey = inReplyToRootProfilePublicKey,
                    inReplyToProfilePublicKey = inReplyToProfilePublicKey
                )
            )
        }

        nostrEvent.toZap()?.let { zap ->
            // Find or create placeHolder note that is being reposted
            val zappedPost = database.postDao().getPostById(zap.postId)

            if (zappedPost == null) {
                // Persist PlaceHolder and sync
                database.postDao().insert(
                    Post(
                        id = zap.postId,
                        createdAt = GENESIS_AT,
                        nostrEventId = nostrEvent.id, // Will get overwriting by sync,
                        content = zap.postId,
                        profilePublicKey = nostrEvent.pubKey // Will get overwriting by sync,
                    )
                )

                // Request a sync for the post being replied to
                if (eventIdsToSync[relayURL] == null) {
                    eventIdsToSync[relayURL] = mutableSetOf()
                }
                eventIdsToSync[relayURL]?.add(zap.postId)
            }

            database.zapDao().upsert(zap)
        }

        if (nostrEvent.kind == ContactListEvent.KIND) {
            val contactListEvent = ContactListEvent(
                id = nostrEvent.id,
                tags = nostrEvent.tags,
                pubKey = nostrEvent.pubKey,
                createdAt = nostrEvent.createdAt.epochSeconds,
                content = nostrEvent.content,
                sig = nostrEvent.sig
            )
            logger.i("Store contact list: $contactListEvent")

            contactListEvent.verifiedFollowKeySet().forEach { followedHexKEys ->

                // Find or create profile with the pubKey... if not found submit a sync request...
                val profile = database.profileDao().getProfileByPublicKey(followedHexKEys)

                if (profile == null) {
                    val placeHolderProfile = Profile(
                        displayName = "LOADING...",
                        publicKey = followedHexKEys,
                        createdAt = GENESIS_AT,
                        nostrEventId = nostrEvent.id,  // Will get overwriting by sync,
                    )
                    database.profileDao().insertPlaceholderProfile(placeHolderProfile)
                    if (profilePublicKeysToSync[relayURL] == null) {
                        profilePublicKeysToSync[relayURL] = mutableSetOf()
                    }
                    profilePublicKeysToSync[relayURL]?.add(followedHexKEys)
                } else if (profile.createdAt == GENESIS_AT && level == 0) {
                    logger.i("${level} This is a placeholder profile... that might need to get synced...: $profile")

                    if (profilePublicKeysToSync[relayURL] == null) {
                        profilePublicKeysToSync[relayURL] = mutableSetOf()
                    }
                    profilePublicKeysToSync[relayURL]?.add(followedHexKEys)
                }

                // Save profile connections...
                database.connectionDao().upsert(
                    Connection(
                        sourcePublicKey = nostrEvent.pubKey,
                        destinationPublicKey = followedHexKEys,
                        nostrEventId = nostrEvent.id
                    )
                )
            }
            // TODO: Delete connections where sourcePublicKey == nostr.pubKey but destinationPublickKey !in verifiedFollowKeySet
        }

        if (level == 0) {
            val negentropySynchronizeRequests = mutableListOf<NegentropySynchronizeRequest>()
            profilePublicKeysToSync.forEach { relayUrlProfilePublicKey ->
                val synchronizationFilter = SynchronizationFilter(
                    authors = relayUrlProfilePublicKey.value.toTypedArray(),
                    kinds = profileEventKinds
                )
                negentropySynchronizeRequests.add(
                    NegentropySynchronizeRequest(
                        id = NegentropySynchronizeRequest.computeId(
                            relayUrlProfilePublicKey.key,
                            synchronizationFilter
                        ),
                        purpose = "synchronization",
                        synchronizationFilter = synchronizationFilter,
                        relayURL = relayUrlProfilePublicKey.key,
                        level = level + 1
                    )
                )
            }

            eventIdsToSync.forEach { relayUrlEventIds ->
                // TODO: Hash relayUrl + synchronicationFilter + ephocMinutes
                val synchronizationFilter = SynchronizationFilter(
                    ids = relayUrlEventIds.value.toTypedArray(),
                )
                Clock.System.now().toEpochMilliseconds()
                negentropySynchronizeRequests.add(
                    NegentropySynchronizeRequest(
                        id = NegentropySynchronizeRequest.computeId(
                            relayUrlEventIds.key,
                            synchronizationFilter
                        ),
                        purpose = "synchronization",
                        synchronizationFilter = synchronizationFilter,
                        relayURL = relayUrlEventIds.key,
                        level = level + 1
                    )
                )
            }

            if (negentropySynchronizeRequests.isNotEmpty()) {
                database.negentropySynchronizeRequestDao().insert(
                    negentropySynchronizeRequests
                )
            }
        }
    }
}