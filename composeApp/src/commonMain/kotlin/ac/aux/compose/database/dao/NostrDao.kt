package ac.aux.compose.database.dao

import ac.aux.compose.database.AuxDatabase
import ac.aux.compose.database.GENESIS_AT
import ac.aux.compose.database.model.BroadcastNostrEventRequest
import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Post
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.SynchronizeNostrEventRequest
import ac.aux.compose.database.model.UnsignedNostrEvent
import ac.aux.compose.database.model.types.SynchronizationFilter
import androidx.room.Dao
import androidx.room.Transaction
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.tags.events.taggedEvents
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUsers
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import kotlin.time.Clock
import kotlin.time.Instant

@Dao
abstract class NostrDao(
    private val database: AuxDatabase
) {
    val logger = Logger.withTag("NostrDao")

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
            synchronizationRelayURLs = relayURLs
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
        synchronizationRelayURLs: List<String>
    ) {
        logger.i("Store Nostr Event: $nostrEvent")

        val storedNostrEvent = database.nostrEventDao().getNostrEventById(nostrEvent.id)

        if (storedNostrEvent == null) {
            // Add new nostr event
            database.nostrEventDao().insert(nostrEvent)
        } else if (nostrEvent.createdAt > storedNostrEvent.createdAt) {
            // Update nostr event
            database.nostrEventDao().upsert(nostrEvent)
        }

        // Index nostrEvent
        indexNostrEvent(
            nostrEvent = nostrEvent,
            synchronizationRelayURLs = synchronizationRelayURLs
        )
    }

    private suspend fun indexNostrEvent(
        nostrEvent: NostrEvent,
        synchronizationRelayURLs: List<String>
    ) {
        if (nostrEvent.unsignedNostrEventId == null) {
            logger.i("Store Nostr Event: $nostrEvent")
            // Find or create profile with the pubKey... if not found submit a sync request...
            val profile = database.profileDao().getProfileByPublicKey(nostrEvent.pubKey)

            if (profile == null) {
                val placeHolderProfile = Profile(
                    publicKey = nostrEvent.pubKey,
                    nostrEventId = nostrEvent.id,  // Will get overwriting by sync,
                )
                database.profileDao().insertPlaceholderProfile(placeHolderProfile)

                database.synchronizeNostrEventRequestDao().insert(
                    synchronizationRelayURLs.map { synchronizationRelayURL ->
                        SynchronizeNostrEventRequest(
                            purpose = "synchronization",
                            synchronizationFilters = arrayOf(
                                SynchronizationFilter(
                                    authors = arrayOf(nostrEvent.pubKey),
                                    kinds = arrayOf(MetadataEvent.KIND)
                                )
                            ),
                            relayURL = synchronizationRelayURL
                        )
                    }
                )
            }
        }

        // Sync tagged events and authors...
        nostrEvent.tags.taggedEvents().forEach { taggedEvent ->
            val taggedNostrEvent = database.nostrEventDao().getNostrEventById(taggedEvent.eventId)
            if (taggedNostrEvent == null) {
                val recommendRelayUrl = taggedEvent.relay?.url

                val synchronizeNostrEventRequests = if (recommendRelayUrl != null) {
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
                            )
                        )
                    } else {
                        synchronizationRelayURLs.map { synchronizationRelayURL ->
                            SynchronizeNostrEventRequest(
                                purpose = "synchronization",
                                synchronizationFilters = arrayOf(
                                    SynchronizationFilter(
                                        ids = arrayOf(taggedEvent.eventId)
                                    )
                                ),
                                relayURL = synchronizationRelayURL
                            )
                        }
                    }

                database.synchronizeNostrEventRequestDao().insert(
                    synchronizeNostrEventRequests
                )
            }

        }
        nostrEvent.tags.taggedUsers().forEach { taggedUser ->
            val recommendedRelay = taggedUser.relayHint?.url

            val synchronizeNostrEventRequests = if (recommendedRelay != null) {
                listOf(
                    SynchronizeNostrEventRequest(
                        purpose = "synchronization",
                        synchronizationFilters = arrayOf(
                            SynchronizationFilter(
                                authors = arrayOf(taggedUser.pubKey),
                                kinds = arrayOf(MetadataEvent.KIND)
                            )
                        ),
                        relayURL = recommendedRelay,
                        isRecommendedRelay = true,
                    )
                )
            } else {
                synchronizationRelayURLs.map { synchronizationRelayURL ->
                    SynchronizeNostrEventRequest(
                        purpose = "synchronization",
                        synchronizationFilters = arrayOf(
                            SynchronizationFilter(
                                authors = arrayOf(taggedUser.pubKey),
                                kinds = arrayOf(MetadataEvent.KIND)
                            )
                        ),
                        relayURL = synchronizationRelayURL
                    )
                }
            }

            database.synchronizeNostrEventRequestDao().insert(
                synchronizeNostrEventRequests
            )
        }
        nostrEvent.toPost()?.let { post ->
            if (post.replyToId != null) {
                // Find or create placeHolder note that is replyTo
                val replyingToPost = database.postDao().getPostById(post.replyToId)

                if (replyingToPost == null) {
                    // Persist PlaceHolder and sync
                    database.postDao().insert(
                        Post(
                            id = post.replyToId,
                            nostrEventId = nostrEvent.id, // Will get overwriting by sync handled by tagged events...
                            content = post.replyToId,
                            profilePublicKey = nostrEvent.pubKey // Will get overwriting by sync handled by tagged events...
                        )
                    )
                }
            }

            database.postDao().upsert(post)
        }

        nostrEvent.toProfile()?.let { profile ->
            database.profileDao().upsert(profile)
        }

        nostrEvent.toReaction()?.let { reaction ->
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
                        publicKey = nostrEvent.pubKey,
                        nostrEventId = nostrEvent.id,  // Will get overwriting by sync handled by tagged events...
                    )
                )

                // Request a sync for the post being replied to
                database.synchronizeNostrEventRequestDao().insert(
                    synchronizationRelayURLs.map { synchronizationRelayURL ->
                        SynchronizeNostrEventRequest(
                            purpose = "synchronization",
                            synchronizationFilters = arrayOf(
                                SynchronizationFilter(
                                    authors = arrayOf(nostrEvent.pubKey),
                                    kinds = arrayOf(MetadataEvent.KIND)
                                )
                            ),
                            relayURL = synchronizationRelayURL
                        )
                    }
                )
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
                        nostrEventId = nostrEvent.id, // Will get overwriting by sync,
                        content = zap.postId,
                        profilePublicKey = nostrEvent.pubKey // Will get overwriting by sync,
                    )
                )

                // Request a sync for the post being replied to
                database.synchronizeNostrEventRequestDao().insert(
                    synchronizationRelayURLs.map { synchronizationRelayURL ->
                        SynchronizeNostrEventRequest(
                            purpose = "synchronization",
                            synchronizationFilters = arrayOf(
                                SynchronizationFilter(
                                    ids = arrayOf(zap.postId)
                                )
                            ),
                            relayURL = synchronizationRelayURL
                        )
                    }
                )
            }

            // TODO: Placeholder for all the other things...
            database.zapDao().upsert(zap)
        }
    }
}