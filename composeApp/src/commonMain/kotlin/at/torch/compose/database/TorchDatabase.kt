package at.torch.compose.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.TypeConverters
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import kotlin.time.Instant

val GENESIS_AT = Instant.fromEpochMilliseconds(1231006505000L)

@Database(
    entities = [
        _root_ide_package_.at.torch.compose.database.model.BroadcastNostrEventReceipt::class,
        _root_ide_package_.at.torch.compose.database.model.BroadcastNostrEventRequest::class,
        _root_ide_package_.at.torch.compose.database.model.Connection::class,
        _root_ide_package_.at.torch.compose.database.model.ChatMessage::class,
        _root_ide_package_.at.torch.compose.database.model.ChatMessageBroadcastNostrEventRequestRelation::class,
        _root_ide_package_.at.torch.compose.database.model.ChatMessageBroadcastNostrEventReceiptRelation::class,
        _root_ide_package_.at.torch.compose.database.model.ChatMessageNostrEventRelation::class,
        _root_ide_package_.at.torch.compose.database.model.ChatRoom::class,
        _root_ide_package_.at.torch.compose.database.model.InReplyToRelation::class,
        _root_ide_package_.at.torch.compose.database.model.GiftWrapMessage::class,
        _root_ide_package_.at.torch.compose.database.model.GiftWrapSeal::class,
        _root_ide_package_.at.torch.compose.database.model.GiftWrapPayload::class,
        _root_ide_package_.at.torch.compose.database.model.Mention::class,
        _root_ide_package_.at.torch.compose.database.model.NegentropySynchronizeRequest::class,
        _root_ide_package_.at.torch.compose.database.model.NegentropySynchronizeResult::class,
        _root_ide_package_.at.torch.compose.database.model.NostrEvent::class,
        _root_ide_package_.at.torch.compose.database.model.NostrEventRelay::class,
        _root_ide_package_.at.torch.compose.database.model.Participant::class,
        _root_ide_package_.at.torch.compose.database.model.Post::class,
        _root_ide_package_.at.torch.compose.database.model.Profile::class,
        _root_ide_package_.at.torch.compose.database.model.QuotedRelation::class,
        _root_ide_package_.at.torch.compose.database.model.Reaction::class,
        _root_ide_package_.at.torch.compose.database.model.RecentSearch::class,
        _root_ide_package_.at.torch.compose.database.model.Relay::class,
        _root_ide_package_.at.torch.compose.database.model.RepostedRelation::class,
        _root_ide_package_.at.torch.compose.database.model.SynchronizeNostrEventRequest::class,
        _root_ide_package_.at.torch.compose.database.model.SynchronizeNostrEventResult::class,
        _root_ide_package_.at.torch.compose.database.model.UnsignedNostrEvent::class,
        _root_ide_package_.at.torch.compose.database.model.Zap::class
    ],
    version = 1
)
@TypeConverters(_root_ide_package_.at.torch.compose.database.converters.AuxConverters::class)
abstract class TorchDatabase: RoomDatabase() {
    abstract fun broadcastNostrEventReceiptDao(): at.torch.compose.database.dao.BroadcastNostrEventReceiptDao
    abstract fun broadcastNostrEventRequestDao(): at.torch.compose.database.dao.BroadcastNostrEventRequestDao

    abstract fun chatMessageDao(): at.torch.compose.database.dao.ChatMessageDao

    abstract fun chatMessageBroadcastNostrEventRequestRelationDao(): at.torch.compose.database.dao.ChatMessageBroadcastNostrEventRequestRelationDao

    abstract fun chatMessageBroadcastNostrEventReceiptRelationDao(): at.torch.compose.database.dao.ChatMessageBroadcastNostrEventReceiptRelationDao

    abstract fun chatMessageNostrEventRelationDao(): at.torch.compose.database.dao.ChatMessageNostrEventRelationDao

    abstract fun chatRoomDao(): at.torch.compose.database.dao.ChatRoomDao

    abstract fun connectionDao(): at.torch.compose.database.dao.ConnectionDao

    abstract fun giftWrapMessageDao(): at.torch.compose.database.dao.GiftWrapMessageDao

    abstract fun giftWrapSealDao(): at.torch.compose.database.dao.GiftWrapSealDao

    abstract fun giftWrapPayloadDao(): at.torch.compose.database.dao.GiftWrapPayloadDao

    abstract fun inReplyToRelationDao(): at.torch.compose.database.dao.InReplyToRelationDao

    abstract fun mentionDao(): at.torch.compose.database.dao.MentionDao

    abstract fun negentropySynchronizeRequestDao(): at.torch.compose.database.dao.NegentropySynchronizeRequestDao

    abstract fun negentropySynchronizeResultDao(): at.torch.compose.database.dao.NegentropySynchronizeResultDao

    abstract fun nostrDao(): at.torch.compose.database.dao.NostrDao

    abstract fun nostrEventDao(): at.torch.compose.database.dao.NostrEventDao

    abstract fun nostrEventRelayDao(): at.torch.compose.database.dao.NostrEventRelayDao

    abstract fun nostrNip17Dao(): at.torch.compose.database.dao.NostrNip17Dao

    abstract fun participantDao(): at.torch.compose.database.dao.ParticipantDao

    abstract fun postDao(): at.torch.compose.database.dao.PostDao

    abstract fun profileDao(): at.torch.compose.database.dao.ProfileDao

    abstract fun quotedRelationDao(): at.torch.compose.database.dao.QuotedRelationDao

    abstract fun reactionDao(): at.torch.compose.database.dao.ReactionDao

    abstract fun recentSearchDao(): at.torch.compose.database.dao.RecentSearchDao

    abstract fun relayDao(): at.torch.compose.database.dao.RelayDao

    abstract fun repostedRelationDao(): at.torch.compose.database.dao.RepostedRelationDao

    abstract fun synchronizeNostrEventRequestDao(): at.torch.compose.database.dao.SynchronizeNostrEventRequestDao

    abstract fun synchronizeNostrEventResultDao(): at.torch.compose.database.dao.SynchronizeNostrEventResultDao

    abstract fun unsignedNostrEventDao(): at.torch.compose.database.dao.UnsignedNostrEventDao
    abstract fun zapDao(): at.torch.compose.database.dao.ZapDao

    suspend fun wipeData() {
        useWriterConnection { transactor ->
            transactor.immediateTransaction {
                // TODO: Actually delete the data...
            }
        }
    }
}