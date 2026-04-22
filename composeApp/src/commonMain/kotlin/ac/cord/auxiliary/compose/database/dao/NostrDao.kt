package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.AuxDatabase
import ac.cord.auxiliary.compose.database.GENESIS_AT
import ac.cord.auxiliary.compose.database.model.BroadcastNostrEventRequest
import ac.cord.auxiliary.compose.database.model.Connection
import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Post
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.SynchronizeNostrEventRequest
import ac.cord.auxiliary.compose.database.model.UnsignedNostrEvent
import ac.cord.auxiliary.compose.database.model.types.SynchronizationFilter
import androidx.room.Dao
import androidx.room.Transaction
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.tags.events.taggedEvents
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUsers
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.ChannelListEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayFeedsListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import kotlin.time.Clock

@Dao
abstract class NostrDao(
    private val database: AuxDatabase
) {
    val logger = Logger.withTag("NostrDao")

    val profileEventKinds = arrayOf(
        MetadataEvent.KIND,
        ContactListEvent.KIND,
        AdvertisedRelayListEvent.KIND,
        ChatMessageRelayListEvent.KIND,
        SearchRelayListEvent.KIND,
        IndexerRelayListEvent.KIND,
        ChannelListEvent.KIND,
        RelayFeedsListEvent.KIND
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
        // Find or create profile with the pubKey... if not found TODO: submit a sync request...
//        val profile = database.profileDao().getProfileByPublicKey(nostrEvent.pubKey)
//
//        if (profile == null) {
//            val placeHolderProfile = Profile(
//                publicKey = nostrEvent.pubKey,
//                nostrEventId = nostrEvent.pubKey // This is illegal... because the nostrEventId should be the one with all the profile information
//            )
//            val placeHolderProfileNostrEvent = NostrEvent(
//                id = placeHolderProfile.publicKey,
//                kind = DraftWrapEvent.KIND,
//                pubKey = placeHolderProfile.publicKey,
//                content = placeHolderProfile.publicKey,
//                createdAt = GENESIS_AT,
//                sig = placeHolderProfile.publicKey,
//                tags = emptyArray(),
//            )
//            database.nostrEventDao().upsert(placeHolderProfileNostrEvent)
//            database.profileDao().upsert(placeHolderProfile)
//
//            // TODO: Sync to find and update profile
//        }

        database.nostrEventDao().upsert(nostrEvent)

        indexNostrEvent(
            nostrEvent = nostrEvent,
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
        synchronizationRelayURLs: List<String>,
        level: Int
    ) {
        logger.i("Store Nostr Event: $nostrEvent")

        val storedNostrEvent = database.nostrEventDao().getNostrEventById(nostrEvent.id)

        if (storedNostrEvent == null) {
            // Add new nostr event
            database.nostrEventDao().insert(nostrEvent)
        } else if (nostrEvent.createdAt > storedNostrEvent.createdAt) {
            // Update nostr event`
            database.nostrEventDao().upsert(nostrEvent)
        } else {
            // Nothing else needs to be done
            val profile = database.profileDao().getProfileByPublicKey(nostrEvent.pubKey)
            if (profile?.createdAt == GENESIS_AT) {
                logger.i("This is a placeholder profile might need to get synced...")
                // Setup the sync here...
            } else {
                logger.i("We have the latest version for: ${nostrEvent.id}")

            }
            return
        }

        // Index nostrEvent
        indexNostrEvent(
            nostrEvent = nostrEvent,
            synchronizationRelayURLs = synchronizationRelayURLs,
            level = level
        )
    }

    private suspend fun indexNostrEvent(
        nostrEvent: NostrEvent,
        synchronizationRelayURLs: List<String>,
        level: Int
    ) {
        val synchronizationFilters = mutableSetOf<SynchronizationFilter>() // Might want to use a RelayURL -> SynchronizationFilters hashMap...

        val profilePublicKeysToSync = mutableSetOf<String>()
        val eventIdsToSync = mutableSetOf<String>()

        if (nostrEvent.unsignedNostrEventId == null) {
            logger.i("Store Nostr Event: $nostrEvent")
            // Find or create profile with the pubKey... if not found submit a sync request...
            val profile = database.profileDao().getProfileByPublicKey(nostrEvent.pubKey)

            if (profile == null) {
                val placeHolderProfile = Profile(
                    publicKey = nostrEvent.pubKey,
                    createdAt = GENESIS_AT,
                    nostrEventId = nostrEvent.id,  // Will get overwriting by sync,
                )
                database.profileDao().insertPlaceholderProfile(placeHolderProfile)

//                database.synchronizeNostrEventRequestDao().insert(
//                    synchronizationRelayURLs.take(1).map { synchronizationRelayURL -> // TODO: Take all relayURLS instead of 1
//                        SynchronizeNostrEventRequest(
//                            purpose = "synchronization",
//                            synchronizationFilters = arrayOf(
//                                SynchronizationFilter(
//                                    authors = arrayOf(nostrEvent.pubKey),
//                                    kinds = profileEventKinds
//                                )
//                            ),
//                            relayURL = synchronizationRelayURL
//                        )
//                    }
//                )
//                synchronizationFilters.add(
//                    SynchronizationFilter(
//                        authors = arrayOf(nostrEvent.pubKey),
//                        kinds = profileEventKinds
//                    )
//                )
                profilePublicKeysToSync.add(nostrEvent.pubKey)
            } else if (profile.createdAt == GENESIS_AT) {
                logger.i("This is a placeholder profile... that might need to get synced...")
                profilePublicKeysToSync.add(nostrEvent.pubKey)
            }
        } else {
            logger.w("We somehow have an unsignedNostrEvent: $nostrEvent")
        }

        // Sync tagged events and authors...
        nostrEvent.tags.taggedEvents().take(4).forEach { taggedEvent -> // We only look at the first 3 tagged events...
            val taggedNostrEvent = database.nostrEventDao().getNostrEventById(taggedEvent.eventId)
            if (taggedNostrEvent == null) {
                val recommendRelayUrl = taggedEvent.relay?.url

                val synchronizeNostrEventRequests = if (recommendRelayUrl != null) {
                        eventIdsToSync.add(taggedEvent.eventId)
                        listOf(
                            SynchronizeNostrEventRequest(
                                purpose = "synchronization",
                                synchronizationFilters = arrayOf(
                                    SynchronizationFilter(
                                        ids = arrayOf(taggedEvent.eventId)
                                    )
                                ),
                                relayURL = recommendRelayUrl,
                                isRecommendedRelay = true,
                                level = level + 1
                            )
                        )
                    } else {
                        eventIdsToSync.add(taggedEvent.eventId)
                        synchronizationRelayURLs.map { synchronizationRelayURL ->
                            SynchronizeNostrEventRequest(
                                purpose = "synchronization",
                                synchronizationFilters = arrayOf(
                                    SynchronizationFilter(
                                        ids = arrayOf(taggedEvent.eventId)
                                    )
                                ),
                                relayURL = synchronizationRelayURL,
                                level = level + 1
                            )
                        }
                    }

                database.synchronizeNostrEventRequestDao().insert(
                    synchronizeNostrEventRequests
                )
            }

        }
        nostrEvent.tags.taggedUsers().take(5).forEach { taggedUser -> // 5 tagged users...
            val recommendedRelay = taggedUser.relayHint?.url

            val synchronizeNostrEventRequests = if (recommendedRelay != null) {
//                listOf(
//                    SynchronizeNostrEventRequest(
//                        purpose = "synchronization",
//                        synchronizationFilters = arrayOf(
//                            SynchronizationFilter(
//                                authors = arrayOf(taggedUser.pubKey),
//                                kinds = profileEventKinds
//                            )
//                        ),
//                        relayURL = recommendedRelay,
//                        isRecommendedRelay = true,
//                    )
//                )
//                synchronizationFilters.add(
//                    SynchronizationFilter(
//                        authors = arrayOf(taggedUser.pubKey),
//                        kinds = profileEventKinds
//                    )
//                )
                profilePublicKeysToSync.add(taggedUser.pubKey)
            } else {
//                synchronizationRelayURLs.take(1).map { synchronizationRelayURL ->
//                    SynchronizeNostrEventRequest(
//                        purpose = "synchronization",
//                        synchronizationFilters = arrayOf(
//                            SynchronizationFilter(
//                                authors = arrayOf(taggedUser.pubKey),
//                                kinds = profileEventKinds
//                            )
//                        ),
//                        relayURL = synchronizationRelayURL
//                    )
//                }
                profilePublicKeysToSync.add(
                    taggedUser.pubKey
                )
//                synchronizationFilters.add(
//                    SynchronizationFilter(
//                        authors = arrayOf(taggedUser.pubKey),
//                        kinds = profileEventKinds
//                    )
//                )
            }

//            database.synchronizeNostrEventRequestDao().insert(
//                synchronizeNostrEventRequests
//            )
        }

        nostrEvent.toProfile()?.let { profile ->
            database.profileDao().upsert(profile)
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

        nostrEvent.toRepost()?.let { repost ->
            // Find or create placeHolder note that is being reposted
            val repostedPost = database.postDao().getPostById(repost.repostedPostId)

            if (repostedPost == null) {
                // Persist PlaceHolder and sync
                database.postDao().insert(
                    Post(
                        id = repost.repostedPostId,
                        createdAt = GENESIS_AT,
                        nostrEventId = nostrEvent.id, // Will get overwriting by sync handled by tagged events...
                        content = repost.repostedPostId,
                        profilePublicKey = nostrEvent.pubKey // Will get overwriting by sync handled by tagged events...
                    )
                )
            }

            val repostedPostProfile = database.profileDao().getProfileByPublicKey(repost.repostedPostAuthorPublicKey)

            if (repostedPostProfile == null) {
                // Persist PlaceHolder and sync
                database.profileDao().insertPlaceholderProfile(
                    Profile(
                        publicKey = repost.repostedPostAuthorPublicKey,
                        createdAt = GENESIS_AT,
                        nostrEventId = nostrEvent.id,  // Will get overwriting by sync handled by tagged events...
                    )
                )

                // Request a sync for the post being replied to
//                database.synchronizeNostrEventRequestDao().insert(
//                    synchronizationRelayURLs.map { synchronizationRelayURL ->
//                        SynchronizeNostrEventRequest(
//                            purpose = "synchronization",
//                            synchronizationFilters = arrayOf(
//
//                            ),
//                            relayURL = synchronizationRelayURL
//                        )
//                    }
//                )
//                synchronizationFilters.add(
//                    SynchronizationFilter(
//                        authors = arrayOf(repost.repostedPostAuthorPublicKey),
//                        kinds = profileEventKinds
//                    )
//                )
                profilePublicKeysToSync.add(
                    repost.repostedPostAuthorPublicKey
                )
            } else if (repostedPostProfile.createdAt == GENESIS_AT) {
                logger.i("This is a placeholder profile... that might need to get synced...")
                profilePublicKeysToSync.add(repost.repostedPostAuthorPublicKey)
            }
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
                eventIdsToSync.add(zap.postId)
//                database.synchronizeNostrEventRequestDao().insert(
//                    synchronizationRelayURLs.map { synchronizationRelayURL ->
//
//                        SynchronizeNostrEventRequest(
//                            purpose = "synchronization",
//                            synchronizationFilters = arrayOf(
//                                SynchronizationFilter(
//                                    ids = arrayOf(zap.postId)
//                                )
//                            ),
//                            relayURL = synchronizationRelayURL,
//                            level = level + 1
//                        )
//                    }
//                )
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
                        publicKey = followedHexKEys,
                        createdAt = GENESIS_AT,
                        nostrEventId = nostrEvent.id,  // Will get overwriting by sync,
                    )
                    database.profileDao().insertPlaceholderProfile(placeHolderProfile)

//                    database.synchronizeNostrEventRequestDao().insert(
//                        synchronizationRelayURLs.take(1).map { synchronizationRelayURL -> // TODO: Get all relayURLs instead of 1
//                            SynchronizeNostrEventRequest(
//                                purpose = "synchronization",
//                                synchronizationFilters = arrayOf(
//                                    SynchronizationFilter(
//                                        authors = arrayOf(followedHexKEys),
//                                        kinds = profileEventKinds
//                                    )
//                                ),
//                                relayURL = synchronizationRelayURL
//                            )
//                        }
//                    )
                    profilePublicKeysToSync.add(followedHexKEys)
                } else if (profile.createdAt == GENESIS_AT) {
                    logger.i("This is a placeholder profile... that might need to get synced...")
                    profilePublicKeysToSync.add(followedHexKEys)
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
            if (profilePublicKeysToSync.isNotEmpty()) {
                synchronizationFilters.add(
                    SynchronizationFilter(
                        authors = profilePublicKeysToSync.take(21).toTypedArray(), // We only sync 21 profiles at a time...
                        kinds = profileEventKinds
                    )
                )
            }

            if (eventIdsToSync.isNotEmpty()) {
                synchronizationFilters.add(
                    SynchronizationFilter(
                        ids = eventIdsToSync.take(21).toTypedArray(), // We should only sync 21 profiles at a time...
                    )
                )
            }
        } else {
            logger.d("We shouldn't sync profiles on level ${level} so we dropping: $profilePublicKeysToSync")
        }



        if (synchronizationFilters.size > 1) {
            logger.d("We have a lot to sync")
        }

        if (synchronizationFilters.isNotEmpty()) {
            logger.d("Synchronize ${level}: $synchronizationFilters")
            synchronizationFilters.forEach { synchronizationFilter ->
                database.synchronizeNostrEventRequestDao().insert(
                    synchronizationRelayURLs.take(1).map { synchronizationRelayURL -> // TODO: Get all relayURLs instead of 1
                        SynchronizeNostrEventRequest(
                            purpose = "synchronization",
                            synchronizationFilters = arrayOf(
                                synchronizationFilter
                            ),
                            relayURL = synchronizationRelayURL,
                            level = level + 1
                        )
                    }
                )
            }
        }

    }
}