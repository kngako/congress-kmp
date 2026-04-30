package ac.cord.auxiliary.compose.database.model

import ac.cord.auxiliary.compose.database.model.traits.BroadcastableEntity
import ac.cord.auxiliary.compose.database.model.traits.LocalStoreEntity
import ac.cord.auxiliary.compose.database.model.traits.SoftDeletableEntity
import ac.cord.auxiliary.compose.database.model.traits.TimestampedEntity
import ac.cord.auxiliary.compose.database.model.traits.UnsignedNostrEventEntity
import ac.cord.auxiliary.compose.database.model.types.SynchronizationFilter
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.tags.events.firstTaggedEvent
import com.vitorpamplona.quartz.nip01Core.tags.events.taggedEvents
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip18Reposts.quotes.QAddressableTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QEventTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.firstTaggedQuote
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.String
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    indices = [
        Index("kind"),
        Index("pubKey"),
        Index("quotedNostrEventId"),
        Index("inReplyToNostrEventId"),
        Index("inReplyToRootNostrEventId"),
    ]
)
data class NostrEvent(
    @PrimaryKey
    val id: String,
    val pubKey: HexKey,
    val kind: Kind,
    val tags: TagArray,
    val content: String,
    val sig: HexKey,

    val quotedNostrEventId: HexKey? = null,
    val quotedAuthorPublicKey: HexKey? = null,

    val inReplyToNostrEventId: HexKey? = null,
    val inReplyToAuthorPublicKey: HexKey? = null,

    val inReplyToRootNostrEventId: HexKey? = null,
    val inReplyToRootAuthorPublicKey: HexKey? = null,

    val repostedNostrEventId: HexKey? = null,
    val repostedAuthorPublicKey: HexKey? = null,

    override val unsignedNostrEventId: Long? = null,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt,
    override val savedAt: Instant = Clock.System.now(),
    override val deletedAt: Instant? = null,
    override val broadcastedAt: Instant? = null,
): UnsignedNostrEventEntity, TimestampedEntity, LocalStoreEntity, BroadcastableEntity, SoftDeletableEntity  {
    @Ignore
    private val logger = Logger.withTag("NostrEvent")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as NostrEvent

        if (kind != other.kind) return false
        if (id != other.id) return false
        if (pubKey != other.pubKey) return false
        if (createdAt != other.createdAt) return false
        if (!tags.contentDeepEquals(other.tags)) return false
        if (content != other.content) return false
        if (sig != other.sig) return false

        return true
    }

    override fun hashCode(): Int {
        var result = kind
        result = 31 * result + id.hashCode()
        result = 31 * result + pubKey.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + tags.contentDeepHashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + sig.hashCode()
        return result
    }

    fun toPost(): Post? = try {
        if (kind == TextNoteEvent.KIND) {
            val textNote = EventFactory.create<TextNoteEvent>(
                id = id,
                pubKey = pubKey,
                createdAt = createdAt.epochSeconds,
                kind = kind,
                tags = tags,
                content = content,
                sig = sig
            )

            return Post(
                id = textNote.id,
                repostId = null,
                quote = null,
                content = textNote.content,
                nostrEventId = id,
                replyToId = textNote.replyingTo(),
                profilePublicKey = textNote.pubKey,
                createdAt = Instant.fromEpochSeconds(textNote.createdAt),
                updatedAt = Instant.fromEpochSeconds(textNote.createdAt),
            )
        } else {
            return null
        }
    } catch (e: Throwable) {
        logger.e("Failed to return Post: ", e)
        return null
    }

    fun toProfile(): Profile? = try {
        if (kind == MetadataEvent.KIND) {
            val metadataEvent = EventFactory.create<MetadataEvent>(
                id = id,
                pubKey = pubKey,
                createdAt = createdAt.epochSeconds,
                kind = kind,
                tags = tags,
                content = content,
                sig = sig
            )

            return metadataEvent.contactMetaData()?.let { userMetadata ->
                return Profile(
                    publicKey = pubKey,
                    nostrEventId = id,

                    createdAt = Instant.fromEpochSeconds(metadataEvent.createdAt),
                    updatedAt = Instant.fromEpochSeconds(metadataEvent.createdAt),

                    userName = userMetadata.name,
                    displayName = userMetadata.displayName,
                    picture = userMetadata.picture,
                    banner = userMetadata.banner,
                    website = userMetadata.website,
                    about = userMetadata.about,
                    bot = userMetadata.bot,
                    pronouns = userMetadata.pronouns,
                    nip05 = userMetadata.nip05,
                    domain = userMetadata.domain,
                    lud06 = userMetadata.lud06,
                    lud16 = userMetadata.lud16,
                    twitter = userMetadata.twitter
                )
            }
        }
        return null
    }  catch (e: Throwable) {
        logger.e("Failed to return Profile: ", e)
        return null
    }

    fun toReaction(): Reaction? = try {
        if (kind == ReactionEvent.KIND) {
            val reactionEvent = EventFactory.create<ReactionEvent>(
                id = id,
                pubKey = pubKey,
                createdAt = createdAt.epochSeconds,
                kind = kind,
                tags = tags,
                content = content,
                sig = sig
            )

            return reactionEvent.originalPost().firstOrNull()?.let { reactedTo ->
                Reaction(
                    id = reactionEvent.id,
                    content = reactionEvent.content,

                    nostrEventId = id,
                    replyToId = reactedTo,
                    profilePublicKey = reactionEvent.pubKey,

                    createdAt = Instant.fromEpochSeconds(reactionEvent.createdAt),
                    updatedAt = Instant.fromEpochSeconds(reactionEvent.createdAt),
                )
            }
        }
        return null
    }  catch (e: Throwable) {
        logger.e("Failed to return Reaction: ", e)
        return null
    }

    fun toRepostedRelation(): RepostedRelation? = try {
        if (kind == RepostEvent.KIND) {
            val repostEvent = EventFactory.create<RepostEvent>(
                id = id,
                pubKey = pubKey,
                createdAt = createdAt.epochSeconds,
                kind = kind,
                tags = tags,
                content = content,
                sig = sig
            )

            return repostEvent.containedPost()?.let { containedPost ->
                RepostedRelation(
                    repostedNostrEventId = containedPost.id,
                    repostedProfilePublicKey = containedPost.pubKey,

                    repostingNostrEventId = repostEvent.id,

                    createdAt = Instant.fromEpochSeconds(repostEvent.createdAt),
                    updatedAt = Instant.fromEpochSeconds(repostEvent.createdAt),
                )

            }
        }

        return null
    } catch (e: Throwable) {
        logger.e("Failed to return Repost: ", e)
        return null
    }

    fun toQuotedRelation(): QuotedRelation? = try {
        if (kind == TextNoteEvent.KIND) {
            tags.firstTaggedQuote()?.let { taggedQuote ->
                when (taggedQuote) {
                    is QEventTag -> {
                        Triple(
                            taggedQuote.eventId,
                            taggedQuote.author,
                            taggedQuote.relay,
                        )
                    }
                    is QAddressableTag -> {
                        Triple(
                            taggedQuote.address.dTag,
                            taggedQuote.address.pubKeyHex,
                            taggedQuote.relay,
                        )
                    }
                    else -> null
                }?.let { quotedEventData ->

                    return QuotedRelation(
                        quotedNostrEventId = quotedEventData.first,
                        quotedProfilePublicKey = quotedEventData.second,

                        quotingNostrEventId = id,

                        createdAt = createdAt
                    )
                }
            }
        }

        // TODO: Get quoted event from content...

        return null
    } catch (e: Throwable) {
        logger.e("Failed to return Repost: ", e)
        return null
    }

    fun toInReplyToRelation(): InReplyToRelation? = try {
        if (kind == RepostEvent.KIND) {
            val textNoteEvent = EventFactory.create<TextNoteEvent>(
                id = id,
                pubKey = pubKey,
                createdAt = createdAt.epochSeconds,
                kind = kind,
                tags = tags,
                content = content,
                sig = sig
            )


            val rootReply = textNoteEvent.markedRoot()
            textNoteEvent.markedReply()?.let { replyTag ->

                return InReplyToRelation(
                    inReplyToNostrEventId = replyTag.eventId,
                    inReplyToProfilePublicKey = replyTag.author,

                    inReplyToRootNostrEventId = rootReply?.eventId,
                    inReplyToRootProfilePublicKey = rootReply?.author,

                    replyingNostrEventId = id
                )
            }
        }

        return null
    } catch (e: Throwable) {
        logger.e("Failed to return Repost: ", e)
        return null
    }
    fun toZap(): Zap? = try {
        if (kind == LnZapEvent.KIND) {
            val zapEvent = EventFactory.create<LnZapEvent>(
                id = id,
                pubKey = pubKey,
                createdAt = createdAt.epochSeconds,
                kind = kind,
                tags = tags,
                content = content,
                sig = sig
            )

            // TODO: Return Zap...
            return null

        }
        return null
    } catch (e: Throwable) {
        logger.e("Failed to return Zap: ", e)
        return null
    }

    fun toNostrJsonObject(): JsonObject {
        val nostrEvent = this

        return buildJsonObject {
            put("id", nostrEvent.id)
            put("pubkey", nostrEvent.pubKey)
            put("created_at", nostrEvent.createdAt.epochSeconds)
            put("kind", nostrEvent.kind)
            putJsonArray("tags") {
                nostrEvent.tags.forEach { tag ->
                    add(
                        JsonArray(tag.map { JsonPrimitive(it) })
                    )
                }
            }
            put("content", nostrEvent.content)
            put("sig", nostrEvent.sig)
        }
    }

    fun toTextNoteEvent(): TextNoteEvent {
        return TextNoteEvent(
            id = id,
            content = content,
            sig = sig,
            tags = tags,
            createdAt = createdAt.epochSeconds,
            pubKey = pubKey
        )
    }

    companion object {

        fun fromEvent(event: Event, synchronizeNostrEventRequest: SynchronizeNostrEventRequest? = null): NostrEvent? = try {
            // TODO: Check for unsupported kind
            val taggedEvent = event.firstTaggedEvent()

            val quotedEvent = when (val taggedQuote = event.firstTaggedQuote()) {
                is QEventTag -> {
                    Triple(
                        taggedQuote.eventId,
                        taggedQuote.author,
                        taggedQuote.relay,
                    )
                }
                is QAddressableTag -> {
                    Triple(
                        taggedQuote.address.dTag,
                        taggedQuote.address.pubKeyHex,
                        taggedQuote.relay,
                    )
                }
                else -> null
            }

            val repostedEvent = if (event.kind == RepostEvent.KIND) {
                val repostedEvent = event.taggedEvents().firstOrNull()
                when {
                    (repostedEvent != null) -> {
                        repostedEvent
                    }
                    else -> null
                }
            } else {
                null
            }

            val (replyToRoot, replyTo) = if (event.kind == TextNoteEvent.KIND) {
                val textNoteEvent = TextNoteEvent(
                    id = event.id,
                    content = event.content,
                    tags = event.tags,
                    createdAt = event.createdAt,
                    sig = event.sig,
                    pubKey = event.pubKey
                )

                Pair(
                    textNoteEvent.markedRoot(),
                    textNoteEvent.markedReply()
                )
            } else {
                Pair(
                    null,
                    null
                )
            }


            return NostrEvent(
                id = event.id,
                pubKey = event.pubKey,
                kind = event.kind,
                content = event.content,
                tags = event.tags,
                createdAt = Instant.fromEpochSeconds(event.createdAt),
                unsignedNostrEventId = synchronizeNostrEventRequest?.unsignedNostrEventId,
                sig = event.sig,

                quotedNostrEventId = quotedEvent?.first,
                quotedAuthorPublicKey = quotedEvent?.second,

                repostedNostrEventId = repostedEvent?.eventId,
                repostedAuthorPublicKey = repostedEvent?.author,

                inReplyToRootNostrEventId = replyToRoot?.eventId,
                inReplyToRootAuthorPublicKey = replyToRoot?.author,

                inReplyToNostrEventId = replyTo?.eventId,
                inReplyToAuthorPublicKey = replyTo?.author,
            )
        } catch (e: Throwable) {
            return null
        }
    }
}