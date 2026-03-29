package ac.aux.compose.database.dao

import ac.aux.compose.database.AuxDatabase
import ac.aux.compose.database.model.BroadcastNostrEventRequest
import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Post
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.SynchronizeNostrEventRequest
import ac.aux.compose.database.model.SynchronizeNostrEventResult
import ac.aux.compose.database.model.UnsignedNostrEvent
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.Transaction
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import kotlinx.coroutines.delay
import kotlin.time.Clock

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
        val profile = database.profileDao().getProfileByPublicKey(nostrEvent.pubKey)

        if (profile == null) {
            val placeHolderProfile = Profile(
                publicKey = nostrEvent.pubKey,
                nostrEventId = nostrEvent.pubKey // This is illegal... because the nostrEventId should be the one with all the profile information
            )
            val placeHolderProfileNostrEvent = NostrEvent(
                id = placeHolderProfile.publicKey,
                kind = 30024,
                pubKey = placeHolderProfile.publicKey,
                content = placeHolderProfile.publicKey,
                createdAt = Clock.System.now(),
                sig = placeHolderProfile.publicKey,
                tags = emptyArray(),
            )
            database.nostrEventDao().upsert(placeHolderProfileNostrEvent)
            database.profileDao().upsert(placeHolderProfile)

            // TODO: Sync to find and update profile
        }

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
        if (nostrEvent.unsignedNostrEventId == null) {
            logger.i("Store Nostr Event: $nostrEvent")
            // Find or create profile with the pubKey... if not found submit a sync request...
            val profile = database.profileDao().getProfileByPublicKey(nostrEvent.pubKey)

            if (profile == null) {
                val placeHolderProfile = Profile(
                    publicKey = nostrEvent.pubKey,
                    nostrEventId = nostrEvent.pubKey // This is illegal... because the nostrEventId should be the one with all the profile information
                )
                val placeHolderProfileNostrEvent = NostrEvent(
                    id = placeHolderProfile.publicKey,
                    kind = 30024,
                    pubKey = placeHolderProfile.publicKey,
                    content = placeHolderProfile.publicKey,
                    createdAt = Clock.System.now(),
                    sig = placeHolderProfile.publicKey,
                    tags = emptyArray(),
                )
                database.nostrEventDao().upsert(placeHolderProfileNostrEvent)
                database.profileDao().upsert(placeHolderProfile)

                synchronizationRelayURLs.forEach { synchronizationRelayURL ->
                    logger.i("Sync Profile with PubKey: ${nostrEvent.pubKey}")
                    database.synchronizeNostrEventRequestDao().upsert(
                        SynchronizeNostrEventRequest(
                            authorPublicKeys = arrayOf(nostrEvent.pubKey),
                            kinds = arrayOf(0),
                            relayURL = synchronizationRelayURL
                        )
                    )
                }
            }
        }
        database.nostrEventDao().upsert(nostrEvent)

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
        nostrEvent.toPost()?.let { post ->
            if (post.replyToId != null) {
                // Find or create placeHolder note that is replyTo
                val replyingToPost = database.postDao().getPostById(post.replyToId)

                if (replyingToPost == null) {
                    // Persist PlaceHolder and sync
                    database.postDao().insert(
                        Post(
                            id = post.replyToId,
                            nostrEventId = nostrEvent.id, // Will get overwriting by sync,
                            content = post.replyToId,
                            profilePublicKey = nostrEvent.pubKey // Will get overwriting by sync,
                        )
                    )

                    // Request a sync for the post being replied to
                    synchronizationRelayURLs.forEach { synchronizationRelayURL ->
                        database.synchronizeNostrEventRequestDao().upsert(
                            SynchronizeNostrEventRequest(
                                eventIds = arrayOf(post.replyToId),
                                relayURL = synchronizationRelayURL
                            )
                        )
                    }
                }
            }

            if (post.repostId != null) {
                val repostPost = database.postDao().getPostById(post.repostId)

                if (repostPost == null) {
                    // Placeholder Post...
                    database.postDao().insert(
                        Post(
                            id = post.repostId,
                            nostrEventId = nostrEvent.id, // Will get overwriting by sync,
                            content = post.repostId,
                            profilePublicKey = nostrEvent.pubKey // Will get overwriting by sync,
                        )
                    )
                    // Request a sync for the post being reposted
                    synchronizationRelayURLs.forEach { synchronizationRelayURL ->
                        database.synchronizeNostrEventRequestDao().upsert(
                            SynchronizeNostrEventRequest(
                                eventIds = arrayOf(post.repostId),
                                relayURL = synchronizationRelayURL
                            )
                        )
                    }
                }
            }
            database.postDao().insert(post)
        }

        nostrEvent.toProfile()?.let { profile ->
            database.profileDao().upsert(profile)
        }

        nostrEvent.toReaction()?.let { reaction ->
            database.reactionDao().upsert(reaction)
        }

        nostrEvent.toRepost()?.let { repost ->
            database.repostDao().upsert(repost)
        }

        nostrEvent.toZap()?.let { zap ->
            database.zapDao().upsert(zap)
        }
    }
}